package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.util.VacancyUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class DouParser implements VacancyParser {

    private static final String DOU_URL = "https://jobs.dou.ua/vacancies/?category=Java&remote";
    private static final long MAX_EXECUTION_TIME_MS = 5 * 60 * 1000;

    private final Map<String, String> sessionCookies = new ConcurrentHashMap<>();

    private long lastRequestTime = 0;
    private final Object rateLimitLock = new Object();

    @Override
    public List<Vacancy> parseVacancies() {
        List<Vacancy> vacancies = new ArrayList<>();
        String currentUserAgent = getRandomUserAgent();
        long startTime = System.currentTimeMillis();

        try {
            log.info("🕵️ DOU: Начинаем парсинг списка вакансий...");
            Document doc = fetchDocumentWithRetry(DOU_URL, currentUserAgent);

            if (doc == null) {
                log.error("❌ DOU: Не удалось получить главную страницу.");
                return vacancies;
            }

            Elements elements = doc.select("li.l-vacancy");

            if (elements.isEmpty()) {
                log.warn("⚠️ DOU: Вакансии не найдены! Возможно, изменилась верстка.");
                return vacancies;
            }

            for (Element element : elements) {
                if (System.currentTimeMillis() - startTime > MAX_EXECUTION_TIME_MS) {
                    log.warn("⏱ DOU: Превышено максимальное время парсинга. Останавливаемся.");
                    break;
                }

                Element titleElement = element.selectFirst(".title a.vt");
                if (titleElement == null) continue;

                String title = titleElement.text();
                String url = titleElement.attr("href");
                String titleLower = title.toLowerCase();

                if (VacancyUtils.shouldIgnore(titleLower)) continue;

                Element dateElement = element.selectFirst(".date");
                String dateText = dateElement != null ? dateElement.text() : "Недавно";

                if (VacancyUtils.isOldVacancy(dateText)) continue;

                Element companyElement = element.selectFirst("a.company");
                String company = companyElement != null ? companyElement.text() : "Не указана";

                Element citiesElement = element.selectFirst(".cities");
                String location = citiesElement != null ? citiesElement.text() : "Уточняйте";

                String role = VacancyUtils.getRole(titleLower);

                simulateHumanBehavior();

                Vacancy vacancy = parseVacancyDetails(title, url, company, location, role, dateText, currentUserAgent);
                if (vacancy != null) {
                    vacancies.add(vacancy);
                }
            }

        } catch (Exception e) {
            log.error("Критическая ошибка парсинга DOU: {}", e.getMessage());
        }

        return vacancies;
    }

    private Vacancy parseVacancyDetails(String title, String url, String company,
                                        String location, String role, String dateText,
                                        String userAgent) {
        String description = "";

        try {
            Document doc = fetchDocumentWithRetry(url, userAgent);

            if (doc != null) {
                Element vacancyText = doc.selectFirst(".b-typo");
                if (vacancyText != null) {
                    description = vacancyText.text();
                }
            }
        } catch (Exception e) {
            // 🔥 Понизили до DEBUG, чтобы не спамить лог (Замечание #5)
            log.debug("Ошибка при парсинге деталей [{}]: {}", url, e.getMessage());
        }

        String experience = VacancyUtils.extractExperience(title + " " + description);

        if ("OVERQUALIFIED".equals(experience)) {
            return null;
        }

        return Vacancy.builder()
                .title(title)
                .company(company)
                .location(location)
                .role(role)
                .experience(experience)
                .postedDate(dateText)
                .url(url)
                .shortDescription(description.length() > 200
                        ? description.substring(0, 200) + "..."
                        : description)
                .siteName(getSiteName())
                .parsedAt(LocalDateTime.now())
                .build();
    }

    private Document fetchDocumentWithRetry(String url, String userAgent) {
        int attempts = 3;
        int cfAttempts = 0; // 🔥 Ограничитель для Cloudflare (Замечание #2)

        for (int i = 1; i <= attempts; i++) {
            enforceGlobalRateLimit();

            try {
                Connection.Response response = Jsoup.connect(url)
                        .userAgent(userAgent)
                        .header("Accept-Language", "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7")
                        .header("Connection", "keep-alive")
                        .cookies(sessionCookies)
                        .timeout(15000)
                        .ignoreHttpErrors(true)
                        .followRedirects(true)
                        .execute();

                sessionCookies.putAll(response.cookies());

                // 🔥 Защита от утечки памяти куки (Замечание #1)
                if (sessionCookies.size() > 50) {
                    sessionCookies.clear();
                    log.debug("♻️ DOU: Cookies очищены (превышен лимит 50)");
                }

                int status = response.statusCode();

                if (status == 429 || status == 403 || status == 503) {
                    // 🔥 Понизили до DEBUG
                    log.debug("⚠️ DOU HTTP {}. Rate Limit. Спим 15 сек...", status);
                    Thread.sleep(15000);
                    continue;
                }

                if (status >= 400) {
                    log.debug("❌ Ошибка HTTP {} по адресу {}", status, url);
                    return null;
                }

                // 🔥 Быстрая проверка без парсинга DOM (Замечание #3)
                String body = response.body();
                if (body.contains("cf-browser-verification") || body.contains("Just a moment")) {
                    cfAttempts++;
                    if (cfAttempts >= 2) {
                        log.warn("🛡️ Cloudflare не пускает (сдаемся после {} попыток) для {}", cfAttempts, url);
                        return null; // 🔥 Не сливаем ресурсы, если защита непробиваема (Замечание #2)
                    }
                    log.debug("🛡️ Cloudflare JS Challenge обнаружен! Отступаем на 15 сек...");
                    Thread.sleep(15000);
                    continue;
                }

                return response.parse(); // Парсим DOM ТОЛЬКО если уверены, что это не заглушка

            } catch (IOException e) {
                log.debug("DOU: Попытка {} не удалась [{}]: {}", i, url, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }

            try {
                long delay = (2000L * i) + ThreadLocalRandom.current().nextInt(1000);
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    private void enforceGlobalRateLimit() {
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long diff = now - lastRequestTime;

            // 🔥 "Плавающий" Rate Limit от 1.2 до 2.0 секунд (Замечание #4)
            long minDelay = 1200 + ThreadLocalRandom.current().nextInt(800);

            if (diff < minDelay) {
                try {
                    Thread.sleep(minDelay - diff);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestTime = System.currentTimeMillis();
        }
    }

    private void simulateHumanBehavior() {
        try {
            int chance = ThreadLocalRandom.current().nextInt(10);
            long delay = switch (chance) {
                case 0, 1 -> 3000 + ThreadLocalRandom.current().nextInt(2000);
                case 2, 3, 4, 5 -> 1000 + ThreadLocalRandom.current().nextInt(1000);
                default -> 500 + ThreadLocalRandom.current().nextInt(500);
            };

            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String getRandomUserAgent() {
        List<String> agents = List.of(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        );
        return agents.get(ThreadLocalRandom.current().nextInt(agents.size()));
    }

    @Override
    public String getSiteName() {
        return "DOU";
    }
}
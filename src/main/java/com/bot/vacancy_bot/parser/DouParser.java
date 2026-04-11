package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.service.PlaywrightService;
import com.bot.vacancy_bot.util.VacancyUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

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

    private final PlaywrightService playwrightService;
    private final Map<String, String> sessionCookies = new ConcurrentHashMap<>();
    private final Object rateLimitLock = new Object();
    private long lastRequestTime = 0;

    public DouParser(PlaywrightService playwrightService) {
        this.playwrightService = playwrightService;
    }

    @Override
    public List<Vacancy> parseVacancies() {
        List<Vacancy> vacancies = new ArrayList<>();
        String currentUserAgent = getRandomUserAgent();
        long startTime = System.currentTimeMillis();

        try {
            log.info("🕵️ DOU: Старт гибридного парсинга...");
            Document doc = fetchDocumentWithRetry(DOU_URL, currentUserAgent);

            if (doc == null) return vacancies;

            // 1. Селектор (ты его уже поменял, он верный)
            Elements elements = doc.select("div.l-items li.l-vacancy");

            if (elements.isEmpty()) {
                log.warn("⚠️ DOU: Список пуст. Возможно, Cloudflare не пробит.");
                return vacancies;
            }

            for (Element element : elements) {
                // Защита по времени
                if (System.currentTimeMillis() - startTime > MAX_EXECUTION_TIME_MS) break;

                Element titleEl = element.selectFirst(".title a.vt");
                if (titleEl == null) continue;

                String title = titleEl.text();
                String titleLower = title.toLowerCase();

                // 🔥 ВОТ ЭТО МЫ ДОБАВИЛИ: Жесткий фильтр по названию
                // Если в заголовке нет Java, Spring или JVM - игнорируем (как тот Web developer)
                if (!titleLower.contains("java") && !titleLower.contains("spring") && !titleLower.contains("jvm")) {
                    log.debug("🚫 Пропускаем нерелевантный мусор: {}", title);
                    continue;
                }

                String url = titleEl.attr("href");

                // Твои стандартные фильтры (ignore list)
                if (VacancyUtils.shouldIgnore(titleLower)) continue;

                simulateHumanBehavior();

                // Переходим к деталям
                Vacancy vacancy = parseVacancyDetails(title, url, element, currentUserAgent);
                if (vacancy != null) {
                    vacancies.add(vacancy);
                }
            }
        } catch (Exception e) {
            log.error("Критическая ошибка DouParser: {}", e.getMessage());
        }
        return vacancies;
    }

    private Vacancy parseVacancyDetails(String title, String url, Element listElement, String userAgent) {
        String description = "";
        try {
            Document doc = fetchDocumentWithRetry(url, userAgent);
            if (doc != null) {
                Element descEl = doc.selectFirst(".b-typo");
                if (descEl != null) description = descEl.text();
            }
        } catch (Exception e) {
            log.debug("Ошибка деталей [{}]: {}", url, e.getMessage());
        }

        String exp = VacancyUtils.extractExperience(title + " " + description);
        if ("OVERQUALIFIED".equals(exp)) return null;

        // 🔥 Оптимизация: убрали двойные вызовы selectFirst (Замечание #1)
        Element compEl = listElement.selectFirst("a.company");
        Element locEl = listElement.selectFirst(".cities");
        Element dateEl = listElement.selectFirst(".date");

        return Vacancy.builder()
                .title(title)
                .company(compEl != null ? compEl.text() : "Не указана")
                .location(locEl != null ? locEl.text() : "Уточняйте")
                .postedDate(dateEl != null ? dateEl.text() : "Недавно")
                .role(VacancyUtils.getRole(title.toLowerCase()))
                .experience(exp).url(url).siteName(getSiteName())
                .shortDescription(description.length() > 200 ? description.substring(0, 200) + "..." : description)
                .parsedAt(LocalDateTime.now()).build();
    }

    private Document fetchDocumentWithRetry(String url, String userAgent) {
        int attempts = 3;
        for (int i = 1; i <= attempts; i++) {
            enforceGlobalRateLimit();
            try {
                Connection.Response response = Jsoup.connect(url)
                        .userAgent(userAgent)
                        .cookies(sessionCookies).timeout(15000)
                        .ignoreHttpErrors(true).execute();

                sessionCookies.putAll(response.cookies());
                if (sessionCookies.size() > 50) sessionCookies.clear();

                int status = response.statusCode();
                String body = response.body(); // 🔥 Кешируем body (Замечание #3)

                // Проверяем на блокировку
                boolean isBlocked = status == 403 || status == 429 || status == 503 ||
                        body.contains("cf-browser-verification") ||
                        body.contains("Just a moment");

                if (isBlocked) {
                    log.debug("🛡 Jsoup встретил преграду (Attempt {}/{}).", i, attempts);
                    // 🔥 Fallback только на последней попытке (Замечание #2)
                    if (i == attempts) {
                        log.warn("🛡 Jsoup не справился. Переход на Playwright для {}", url);
                        return fetchWithPlaywright(url, userAgent);
                    }
                    // Если не последняя попытка — просто ждем и пробуем Jsoup снова
                    Thread.sleep(3000L * i);
                    continue;
                }

                return response.parse();
            } catch (Exception e) {
                log.debug("Jsoup attempt {} failed", i);
            }
        }
        return null;
    }

    private Document fetchWithPlaywright(String url, String userAgent) {
        String html = playwrightService.fetchPage(url, userAgent);
        if (html == null) {
            log.warn("❌ Playwright вернул пустой HTML для {}", url);
            return null;
        }

        Document doc = Jsoup.parse(html);
        // 🔥 Усиленная проверка результата (Замечание #5)
        boolean hasData = url.contains("vacancies/?")
                ? doc.selectFirst("li.l-vacancy") != null
                : doc.selectFirst(".b-typo") != null;

        if (!hasData || doc.title().contains("Just a moment")) {
            log.error("🛡️ Playwright Fallback failed for {}", url);
            return null;
        }
        return doc;
    }

    private void enforceGlobalRateLimit() {
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long minDelay = 1200 + ThreadLocalRandom.current().nextInt(800);
            if (now - lastRequestTime < minDelay) {
                try { Thread.sleep(minDelay - (now - lastRequestTime)); } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
    }

    private String getRandomUserAgent() {
        List<String> agents = List.of(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        );
        // 🔥 Динамический размер списка (Замечание #6)
        return agents.get(ThreadLocalRandom.current().nextInt(agents.size()));
    }

    @Override
    public String getSiteName() { return "DOU"; }
}
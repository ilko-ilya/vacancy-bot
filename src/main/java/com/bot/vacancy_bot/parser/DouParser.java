package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.util.VacancyUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class DouParser implements VacancyParser {

    private static final String DOU_URL = "https://jobs.dou.ua/vacancies/?category=Java&remote";

    private final ExecutorService douParserExecutor;

    public DouParser(@Qualifier("douParserExecutor") ExecutorService douParserExecutor) {
        this.douParserExecutor = douParserExecutor;
    }

    @Override
    public List<Vacancy> parseVacancies() {
        List<Vacancy> vacancies = new ArrayList<>();

        try {
            // 1. Загружаем главную страницу с игнорированием ошибок HTTP и редиректами
            Document doc = Jsoup.connect(DOU_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(10000)
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .get();

            Elements elements = doc.select("li.l-vacancy");
            List<Future<Vacancy>> futures = new ArrayList<>();

            for (Element element : elements) {
                Element titleElement = element.selectFirst(".title a.vt");
                if (titleElement == null) continue;

                String title = titleElement.text();
                String url = titleElement.attr("href");
                String titleLower = title.toLowerCase();

                // Пре-фильтрация: игнорируем неподходящие технологии
                if (VacancyUtils.shouldIgnore(titleLower)) {
                    continue;
                }

                Element dateElement = element.selectFirst(".date");
                String dateText = dateElement != null ? dateElement.text() : "Недавно";

                // Пре-фильтрация: игнорируем старые вакансии
                if (VacancyUtils.isOldVacancy(dateText)) {
                    continue;
                }

                Element companyElement = element.selectFirst("a.company");
                String company = companyElement != null ? companyElement.text() : "Не указана";

                Element citiesElement = element.selectFirst(".cities");
                String location = (citiesElement != null) ? citiesElement.text() : "Уточняйте";

                String role = VacancyUtils.getRole(titleLower);

                // 2. Формируем задачу (Callable) для парсинга отдельной страницы
                Callable<Vacancy> task = () -> {
                    String description = "";
                    String experience = "Не указан";

                    try {
                        Document vacancyPage = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                .timeout(10000)
                                .ignoreHttpErrors(true)
                                .followRedirects(true)
                                .get();

                        Element vacancyText = vacancyPage.selectFirst(".b-typo");
                        if (vacancyText != null) {
                            description = vacancyText.text();
                            experience = VacancyUtils.extractExperience(description);
                        }

                    } catch (Exception e) {
                        log.warn("Ошибка при открытии детализации вакансии [{}]: {}", url, e.getMessage());
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
                };

                // 3. Отправляем задачу в наш пул потоков
                futures.add(douParserExecutor.submit(task));
            }

            // 4. Ожидаем результаты с жестким таймаутом (15 секунд)
            for (Future<Vacancy> future : futures) {
                try {
                    Vacancy vacancy = future.get(15, TimeUnit.SECONDS);
                    if (vacancy != null) {
                        vacancies.add(vacancy);
                    }
                } catch (TimeoutException e) {
                    log.warn("Таймаут при парсинге страницы DOU (15 сек). Задача отменена.");
                    future.cancel(true); // Убиваем зависший поток
                } catch (Exception e) {
                    log.warn("Ошибка при получении результата параллельного парсинга: {}", e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("Критическая ошибка при парсинге списка DOU: {}", e.getMessage());
        }

        return vacancies;
    }

    @Override
    public String getSiteName() {
        return "DOU";
    }
}
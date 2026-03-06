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

                if (VacancyUtils.shouldIgnore(titleLower)) {
                    continue;
                }

                Element dateElement = element.selectFirst(".date");
                String dateText = dateElement != null ? dateElement.text() : "Недавно";

                if (VacancyUtils.isOldVacancy(dateText)) {
                    continue;
                }

                Element companyElement = element.selectFirst("a.company");
                String company = companyElement != null ? companyElement.text() : "Не указана";

                Element citiesElement = element.selectFirst(".cities");
                String location = (citiesElement != null) ? citiesElement.text() : "Уточняйте";

                String role = VacancyUtils.getRole(titleLower);

                // --- ЗАДАЧА ДЛЯ ПАРАЛЛЕЛЬНОГО ВЫПОЛНЕНИЯ ---
                Callable<Vacancy> task = () -> {
                    String description = "";

                    try {
                        Document vacancyPage = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0")
                                .timeout(10000)
                                .ignoreHttpErrors(true)
                                .followRedirects(true)
                                .get();

                        Element vacancyText = vacancyPage.selectFirst(".b-typo");
                        if (vacancyText != null) {
                            description = vacancyText.text();
                        }
                    } catch (Exception e) {
                        log.warn("Ошибка при открытии детализации вакансии [{}]: {}", url, e.getMessage());
                    }

                    // 1. Скармливаем утилите заголовок + описание (даже если оно пустое из-за ошибки)
                    String experience = VacancyUtils.extractExperience(title + " " + description);

                    // 2. Если сеньор - убиваем вакансию
                    if ("OVERQUALIFIED".equals(experience)) {
                        return null;
                    }

                    // 3. Возвращаем чистую вакансию
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

                futures.add(douParserExecutor.submit(task));
            }

            for (Future<Vacancy> future : futures) {
                try {
                    Vacancy vacancy = future.get(15, TimeUnit.SECONDS);
                    if (vacancy != null) {
                        vacancies.add(vacancy);
                    }
                } catch (TimeoutException e) {
                    log.warn("Таймаут при парсинге страницы DOU (15 сек). Задача отменена.");
                    future.cancel(true);
                } catch (Exception e) {
                    log.warn("Ошибка при получении результата: {}", e.getMessage());
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
package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.util.VacancyUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class DjinniParser implements VacancyParser {

    private static final String DJINNI_RSS_URL = "https://djinni.co/jobs/rss/?primary_keyword=Java&employment=remote";

    @Override
    public List<Vacancy> parseVacancies() {
        List<Vacancy> vacancies = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(DJINNI_RSS_URL)
                    .parser(Parser.xmlParser())
                    .get();

            Elements items = doc.select("item");

            for (Element item : items) {
                String titleFull = Objects.requireNonNull(item.selectFirst("title")).text();
                String url = Objects.requireNonNull(item.selectFirst("link")).text();
                String pubDate = Objects.requireNonNull(item.selectFirst("pubDate")).text();

                String descriptionHtml = Objects.requireNonNull(item.selectFirst("description")).text();
                String descriptionText = Jsoup.parse(descriptionHtml).text();

                String title = titleFull;
                String company = "Не указана";
                String location = "Remote";

                // 1. Пытаемся достать компанию из RSS
                String normalizedTitle = titleFull.replaceAll("\\s+", " ");
                if (normalizedTitle.contains(" at ")) {
                    String[] parts = normalizedTitle.split(" at ");
                    title = parts[0].trim();
                    if (parts.length > 1) {
                        company = parts[1].trim();
                    }
                }

                // 🔴 2. УМНЫЙ БЛОК: Если компании в RSS нет, идем прямо на страницу вакансии!
                if (company.equals("Не указана")) {
                    try {
                        Document page = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                .get();

                        // Заголовок страницы всегда формата "Title at Company | Djinni"
                        String pageTitle = page.title();
                        if (pageTitle.contains(" at ") && pageTitle.contains(" | Djinni")) {
                            String afterAt = pageTitle.substring(pageTitle.lastIndexOf(" at ") + 4);
                            company = afterAt.replace(" | Djinni", "").trim();
                        }
                    } catch (Exception e) {
                        log.warn("Не удалось перейти на страницу Djinni для поиска компании: {}", url);
                    }
                }

                String titleLower = title.toLowerCase();

                if (VacancyUtils.shouldIgnore(titleLower)) {
                    continue;
                }

                String role = VacancyUtils.getRole(titleLower);
                String experience = VacancyUtils.extractExperience(descriptionText);
                String cleanDate = pubDate.length() > 16 ? pubDate.substring(0, 16) : pubDate;

                if (VacancyUtils.isOldVacancy(cleanDate)) {
                    continue;
                }

                Vacancy vacancy = Vacancy.builder()
                        .title(title)
                        .company(company)
                        .location(location)
                        .role(role)
                        .experience(experience)
                        .postedDate(cleanDate)
                        .url(url)
                        .shortDescription("")
                        .siteName(getSiteName())
                        .parsedAt(LocalDateTime.now())
                        .build();

                vacancies.add(vacancy);
            }
        } catch (Exception e) {
            log.error("Ошибка при парсинге Djinni: {}", e.getMessage());
        }
        return vacancies;
    }

    @Override
    public String getSiteName() {
        return "Djinni";
    }
}
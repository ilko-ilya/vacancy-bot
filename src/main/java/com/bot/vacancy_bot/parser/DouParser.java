package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.service.ScraperApiClient;
import com.bot.vacancy_bot.util.VacancyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class DouParser implements VacancyParser {

    private static final String TARGET_URL = "https://jobs.dou.ua/vacancies/feeds/?category=Java";

    // Внедряем наш новый умный клиент
    private final ScraperApiClient scraperClient;

    @Override
    public boolean isHeavy() {
        return true;
    }

    @Override
    public List<Vacancy> parseVacancies() {
        List<Vacancy> vacancies = new ArrayList<>();
        log.info("⚡ DOU: Старт парсинга...");

        // Вся магия сети, ретраев и эскалаций теперь под капотом одной строки
        Document doc = scraperClient.fetchXmlDocument(TARGET_URL);

        if (Objects.isNull(doc)) {
            log.warn("⚠️ DOU: Не удалось получить документ, прерываем парсинг.");
            return vacancies;
        }

        try {
            Elements items = doc.select("item");

            if (items.isEmpty()) {
                log.warn("⚠️ DOU: RSS-лента пуста.");
                return vacancies;
            }

            for (Element item : items) {
                String title = item.select("title").text();
                String link = item.select("link").text();
                String description = item.select("description").text();
                String pubDate = item.select("pubDate").text();

                String titleLower = title.toLowerCase();

                if (!titleLower.contains("java") && !titleLower.contains("spring") && !titleLower.contains("jvm")) {
                    continue;
                }

                if (VacancyUtils.shouldIgnore(titleLower)) continue;

                String exp = VacancyUtils.extractExperience(title + " " + description);
                if ("OVERQUALIFIED".equals(exp)) continue;

                String company = extractCompanyFromTitle(title);
                String cleanDescription = Jsoup.parse(description).text();

                Vacancy vacancy = Vacancy.builder()
                        .title(title)
                        .company(company)
                        .location("Уточняйте")
                        .postedDate(pubDate)
                        .role(VacancyUtils.getRole(titleLower))
                        .experience(exp)
                        .url(link)
                        .siteName(getSiteName())
                        .shortDescription(cleanDescription.length() > 200 ? cleanDescription.substring(0, 200) + "..." : cleanDescription)
                        .parsedAt(LocalDateTime.now())
                        .build();

                vacancies.add(vacancy);
            }

            log.info("✅ DOU: Обработано {} вакансий", vacancies.size());

        } catch (Exception e) {
            log.error("❌ Ошибка обработки XML от DOU: {}", e.getMessage());
        }

        return vacancies;
    }

    private String extractCompanyFromTitle(String title) {
        if (title.contains(" в ")) {
            return title.substring(title.lastIndexOf(" в ") + 3).trim();
        } else if (title.contains(" at ")) {
            return title.substring(title.lastIndexOf(" at ") + 4).trim();
        }
        return "Не указана";
    }

    @Override
    public String getSiteName() {
        return "DOU";
    }
}

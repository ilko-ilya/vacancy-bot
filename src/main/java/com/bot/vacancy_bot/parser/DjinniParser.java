package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.util.VacancyUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper mapper = new ObjectMapper(); // Jackson для JSON-LD

    @Override
    public List<Vacancy> parseVacancies() {
        List<Vacancy> vacancies = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(DJINNI_RSS_URL).parser(Parser.xmlParser()).get();
            Elements items = doc.select("item");

            for (Element item : items) {
                String titleFull = Objects.requireNonNull(item.selectFirst("title")).text();
                String url = Objects.requireNonNull(item.selectFirst("link")).text();
                String pubDate = Objects.requireNonNull(item.selectFirst("pubDate")).text();

                // 1. Правильный парсинг описания (совет 1)
                String descriptionHtml = Objects.requireNonNull(item.selectFirst("description")).html();
                String descriptionText = Jsoup.parse(descriptionHtml).text();

                String title = titleFull;
                String company = "Не указана";

                // 2. Достаем компанию из <author> (совет 2)
                Element author = item.selectFirst("author");
                if (author != null && !author.text().isBlank()) {
                    company = author.text().trim();
                } else {
                    String[] parts = titleFull.split("(?i)\\s+at\\s+");
                    if (parts.length > 1) {
                        title = parts[0].trim();
                        company = parts[1].trim();
                    }
                }

                // 3. Используем флаг для проверки (совет 3)
                boolean companyMissing = company.equals("Не указана") || company.toLowerCase().contains("hidden");

                if (companyMissing) {
                    try {
                        Document page = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0")
                                .timeout(5000).get();

                        // 4. Используем Jackson для JSON-LD (совет 4)
                        Element jsonLdScript = page.selectFirst("script[type='application/ld+json']");
                        if (jsonLdScript != null) {
                            JsonNode root = mapper.readTree(jsonLdScript.html());
                            JsonNode hiringOrg = root.path("hiringOrganization").path("name");
                            if (!hiringOrg.isMissingNode()) {
                                company = hiringOrg.asText();
                                companyMissing = false; // Нашли!
                            }
                        }

                        // 5. План Б: точный селектор (совет 5)
                        if (companyMissing) {
                            Element companyEl = page.selectFirst("[data-test='company-name']");
                            if (companyEl != null) {
                                company = companyEl.text().trim();
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Djinni: не удалось прочитать страницу {}", url);
                    }
                }

                if (company.equals("Не указана")) company = "Hidden Company (Djinni)";

                // Твоя стандартная логика фильтрации
                String titleLower = title.toLowerCase();
                if (VacancyUtils.shouldIgnore(titleLower)) continue;

                String cleanDate = pubDate.length() > 16 ? pubDate.substring(0, 16) : pubDate;
                if (VacancyUtils.isOldVacancy(cleanDate)) continue;

                vacancies.add(Vacancy.builder()
                        .title(title).company(company).location("Remote")
                        .role(VacancyUtils.getRole(titleLower))
                        .experience(VacancyUtils.extractExperience(title + " " + descriptionText))
                        .postedDate(cleanDate).url(url).siteName(getSiteName())
                        .parsedAt(LocalDateTime.now()).build());
            }
        } catch (Exception e) {
            log.error("Ошибка Djinni: {}", e.getMessage());
        }
        return vacancies;
    }

    @Override
    public String getSiteName() {
        return "Djinni";
    }
}
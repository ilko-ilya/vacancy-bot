package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.util.VacancyUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DouParser implements VacancyParser {

    private static final String SCRAPER_API_KEY = "181616405e1092802cc9af41bbb775e1";

    // Ссылка на чистый XML фид DOU
    private static final String TARGET_URL = "https://jobs.dou.ua/vacancies/feeds/?category=Java";

    @Override
    public List<Vacancy> parseVacancies() {
        List<Vacancy> vacancies = new ArrayList<>();
        log.info("⚡ DOU: Старт парсинга RSS через ScraperAPI...");

        // Формируем хитрую ссылку для прокси
        String proxyUrl = "http://api.scraperapi.com/?api_key=" + SCRAPER_API_KEY + "&url=" + TARGET_URL;

        try {
            // Подключаемся к прокси и ждем (ScraperAPI может думать до 60 сек)
            Document doc = Jsoup.connect(proxyUrl)
                    .timeout(60000)
                    .parser(Parser.xmlParser()) // Обязательно парсим как XML
                    .get();

            Elements items = doc.select("item");

            if (items.isEmpty()) {
                log.warn("⚠️ DOU: RSS-лента пуста или ScraperAPI вернул пустой ответ.");
                return vacancies;
            }

            for (Element item : items) {
                String title = item.select("title").text();
                String link = item.select("link").text();
                String description = item.select("description").text();
                String pubDate = item.select("pubDate").text();

                String titleLower = title.toLowerCase();

                // Твой жесткий фильтр по названию
                if (!titleLower.contains("java") && !titleLower.contains("spring") && !titleLower.contains("jvm")) {
                    continue;
                }

                // Игнор-лист из твоего утилитного класса
                if (VacancyUtils.shouldIgnore(titleLower)) continue;

                // Извлекаем опыт
                String exp = VacancyUtils.extractExperience(title + " " + description);
                if ("OVERQUALIFIED".equals(exp)) continue;

                // В RSS компания обычно указана в заголовке после "в " или "at "
                String company = extractCompanyFromTitle(title);

                // Чистим описание от HTML-тегов, которые могут прийти из RSS
                String cleanDescription = Jsoup.parse(description).text();

                Vacancy vacancy = Vacancy.builder()
                        .title(title)
                        .company(company)
                        .location("Уточняйте") // В RSS нет отдельного поля для города
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

            log.info("✅ DOU: Успешно получено {} вакансий через ScraperAPI", vacancies.size());

        } catch (Exception e) {
            log.error("❌ Критическая ошибка DouParser (ScraperAPI): {}", e.getMessage());
        }

        return vacancies;
    }

    // Хелпер для извлечения названия компании из заголовка
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

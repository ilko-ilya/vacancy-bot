package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.util.VacancyUtils;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class DouParser implements VacancyParser {

    // URL RSS-ленты DOU (чистый XML, без Cloudflare)
    private static final String DOU_RSS_URL = """
            https://jobs.dou.ua/vacancies/feeds/?category=Java""";

    @Override
    public List<Vacancy> parseVacancies() {
        List<Vacancy> vacancies = new ArrayList<>();
        log.info("⚡ DOU: Старт быстрого парсинга через RSS...");

        try {
            // Подключаемся и парсим страницу именно как XML
            // Подключаемся, маскируясь под обычный браузер
            Document doc = Jsoup.connect(DOU_RSS_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                    .header("Accept-Language", "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7")
                    .parser(Parser.xmlParser())
                    .timeout(15000)
                    .get();

            Elements items = doc.select("item");

            if (items.isEmpty()) {
                log.warn("⚠️ DOU: RSS-лента пуста. Возможно, изменился формат.");
                return vacancies;
            }

            for (Element item : items) {
                String title = item.select("title").text();
                String link = item.select("link").text();
                String description = item.select("description").text(); // В RSS тут лежит HTML-описание
                String pubDate = item.select("pubDate").text();

                String titleLower = title.toLowerCase();

                // Твой жесткий фильтр по названию
                if (!titleLower.contains("java") && !titleLower.contains("spring") && !titleLower.contains("jvm")) {
                    continue;
                }

                // Игнор-лист
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

            log.info("✅ DOU: Успешно получено {} вакансий из RSS", vacancies.size());

        } catch (Exception e) {
            log.error("❌ Критическая ошибка DouParser (RSS): {}", e.getMessage());
        }

        return vacancies;
    }

    // Хелпер для извлечения названия компании из заголовка (например, "Java Developer в DataArt")
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

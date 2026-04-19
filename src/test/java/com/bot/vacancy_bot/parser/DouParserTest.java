package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.service.ScraperApiClient;
import com.bot.vacancy_bot.util.VacancyUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DouParserTest {

    @Mock
    private ScraperApiClient scraperApiClient; // Подменяем реальный клиент заглушкой

    @InjectMocks
    private DouParser douParser; // Спринг сам подставит заглушку в конструктор парсера

    @Test
    void testParseVacanciesMocked() {
        System.out.println("=== СТАРТ ТЕСТА DOU PARSER (С Mockito) ===");

        // 1. Подготавливаем фейковый XML ответ, как будто он пришел от DOU
        String mockXml = """
                <rss>
                    <channel>
                        <item>
                            <title>Middle Java Developer в CoolCompany</title>
                            <link>https://jobs.dou.ua/123</link>
                            <description>Шукаємо розробника з досвідом від 2 років.</description>
                            <pubDate>Thu, 19 Apr 2026 12:00:00 +0300</pubDate>
                        </item>
                        <item>
                            <title>Senior Java Developer (Overqualified)</title>
                            <link>https://jobs.dou.ua/456</link>
                            <description>Вимоги: 5+ років досвіду.</description>
                            <pubDate>Thu, 19 Apr 2026 10:00:00 +0300</pubDate>
                        </item>
                        <item>
                            <title>Python Developer (Мусор)</title>
                            <link>https://jobs.dou.ua/789</link>
                            <description>Тут немає джави.</description>
                            <pubDate>Thu, 19 Apr 2026 09:00:00 +0300</pubDate>
                        </item>
                    </channel>
                </rss>
                """;

        Document mockDoc = Jsoup.parse(mockXml, "", Parser.xmlParser());

        // 2. Учим нашу заглушку возвращать этот XML при любом вызове
        when(scraperApiClient.fetchXmlDocument(anyString())).thenReturn(mockDoc);

        // 3. Вызываем метод парсинга
        List<Vacancy> vacancies = douParser.parseVacancies();

        System.out.println("Найдено вакансий после фильтрации: " + vacancies.size());
        for (Vacancy v : vacancies) {
            System.out.println("- " + v.getTitle() + " | " + v.getCompany());
        }

        // 4. Проверяем, что логика фильтрации отработала верно
        assertNotNull(vacancies);
        assertEquals(1, vacancies.size(), "Должна остаться только 1 подходящая вакансия");
        assertEquals("Middle Java Developer в CoolCompany", vacancies.get(0).getTitle());
        assertEquals("CoolCompany", vacancies.get(0).getCompany());

        System.out.println("====================================\n");
    }

    @Test
    void testExtractExperienceLogic() {
        System.out.println("=== СТАРТ ТЕСТА УМНОГО ФИЛЬТРА ОПЫТА ===");

        // Для статических утилит Mockito не нужен, тестируем напрямую

        String text1 = "Шукаємо розробника. Вимоги: від 2 років досвіду з Java.";
        String exp1 = VacancyUtils.extractExperience(text1);
        System.out.println("Тест 1 (2 года): " + exp1);
        assertNotEquals("OVERQUALIFIED", exp1);

        String text2 = "Looking for a developer with 5+ years of experience in Spring.";
        String exp2 = VacancyUtils.extractExperience(text2);
        System.out.println("Тест 2 (5+ лет): " + exp2);
        assertEquals("OVERQUALIFIED", exp2);

        String text3 = "Відкрита позиція Trainee Java Developer. Без комерційного досвіду.";
        String exp3 = VacancyUtils.extractExperience(text3);
        System.out.println("Тест 3 (Trainee): " + exp3);
        assertNotEquals("OVERQUALIFIED", exp3);

        String text4 = "Required: 3 years of commercial experience";
        String exp4 = VacancyUtils.extractExperience(text4);
        System.out.println("Тест 4 (3 года): " + exp4);
        assertNotEquals("OVERQUALIFIED", exp4);

        String text5 = "4+ years of experience. You will mentor interns and juniors.";
        String exp5 = VacancyUtils.extractExperience(text5);
        System.out.println("Тест 5 (4 года + intern): " + exp5);
        assertEquals("OVERQUALIFIED", exp5); // Тут зависит от того, как именно настроен твой фильтр

        System.out.println("========================================");
    }
}
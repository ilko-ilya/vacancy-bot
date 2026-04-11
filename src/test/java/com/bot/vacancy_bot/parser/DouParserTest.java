package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.util.VacancyUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DouParserTest {

    @Test
    void testDouParserFiltersOverqualified() {
        System.out.println("=== СТАРТ ТЕСТА DOU PARSER ===");

        DouParser parser = new DouParser();

        List<Vacancy> vacancies = parser.parseVacancies();

        System.out.println("Найдено подходящих вакансий: " + vacancies.size());

        for (Vacancy v : vacancies) {
            System.out.println("- " + v.getTitle() + " | " + v.getCompany());
        }

        assertNotNull(vacancies);

        System.out.println("====================================\n");
    }

    @Test
    void testParseVacancies() {
        System.out.println("=== НАЧИНАЕМ ТЕСТ ПАРСЕРА DOU ===");
        long startTime = System.currentTimeMillis();

        DouParser douParser = new DouParser();

        // 3. Запускаем парсинг
        List<Vacancy> vacancies = douParser.parseVacancies();

        long endTime = System.currentTimeMillis();

        System.out.println("=== РЕЗУЛЬТАТЫ ===");
        System.out.println("Найдено вакансий: " + vacancies.size());
        System.out.println("Время выполнения: " + (endTime - startTime) + " мс");

        // 4. Выводим результаты
        vacancies.stream().limit(5).forEach(v -> {
            System.out.println("--------------------------------------------------");
            System.out.println("Должность: " + v.getTitle());
            System.out.println("Компания:  " + v.getCompany());
            System.out.println("Опыт:      " + v.getExperience());
            System.out.println("Ссылка:    " + v.getUrl());
        });

    }



    @Test
    void testExtractExperienceLogic() {
        System.out.println("=== СТАРТ ТЕСТА УМНОГО ФИЛЬТРА ОПЫТА ===");

        // 1. Идеальный кандидат (ровно 2 года)
        String text1 = "Шукаємо розробника. Вимоги: від 2 років досвіду з Java.";
        System.out.println("Тест 1 (2 года): " + VacancyUtils.extractExperience(text1));

        // 2. Слишком опытный (сеньор, 5 лет)
        String text2 = "Looking for a developer with 5+ years of experience in Spring.";
        System.out.println("Тест 2 (5+ лет): " + VacancyUtils.extractExperience(text2));

        // 3. Стажер (без цифр, но с ключевыми словами)
        String text3 = "Відкрита позиція Trainee Java Developer. Без комерційного досвіду.";
        System.out.println("Тест 3 (Trainee): " + VacancyUtils.extractExperience(text3));

        // 4. Пограничный случай (ровно 3 года - должно пройти!)
        String text4 = "Required: 3 years of commercial experience";
        System.out.println("Тест 4 (3 года): " + VacancyUtils.extractExperience(text4));

        // 5. Опасный ложный стажер (как было с Intelliarts: 4 года опыта, но есть слово intern)
        String text5 = "4+ years of experience. You will mentor interns and juniors.";
        System.out.println("Тест 5 (4 года + intern): " + VacancyUtils.extractExperience(text5));

        System.out.println("========================================");
    }

}
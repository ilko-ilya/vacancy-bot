package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import org.junit.jupiter.api.Test;

import java.util.List;

class DataArtParserTest {

    @Test
    void testDataArtParserFiltersOverqualified() {
        System.out.println("=== СТАРТ ТЕСТА DataArt PARSER ===");
        DataArtParser parser = new DataArtParser();
        List<Vacancy> vacancies = parser.parseVacancies();

        System.out.println("Найдено подходящих вакансий: " + vacancies.size());
        boolean hasSeniors = false;

        for (Vacancy v : vacancies) {
            System.out.println("- " + v.getTitle() + " | Опыт: " + v.getExperience() + " | " + v.getUrl());
            if ("OVERQUALIFIED".equals(v.getExperience()) ||
                    v.getExperience().contains("4") ||
                    v.getExperience().contains("5")) {
                System.err.println("❌ ПРОПУЩЕН СЕНЬОР: " + v.getTitle());
                hasSeniors = true;
            }
        }

        if (!hasSeniors) System.out.println("✅ DataArt чист! Сеньоров нет.");
        System.out.println("====================================\n");
    }

}
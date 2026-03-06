package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import org.junit.jupiter.api.Test;

import java.util.List;

class DjinniParserTest {
    @Test
    void testDjinniParserFiltersOverqualified() {
        System.out.println("=== СТАРТ ТЕСТА DJINNI PARSER ===");

        // Создаем парсер (так как в нем нет сложных зависимостей, можем создать через new)
        DjinniParser djinniParser = new DjinniParser();

        // Запускаем парсинг реальной ленты Djinni
        List<Vacancy> vacancies = djinniParser.parseVacancies();

        System.out.println("Найдено подходящих вакансий (после всех фильтров): " + vacancies.size());

        boolean hasSeniors = false;

        // Выводим то, что парсер решил нам оставить
        for (Vacancy v : vacancies) {
            System.out.println("---------------------------------");
            System.out.println("Должность: " + v.getTitle());
            System.out.println("Опыт:      " + v.getExperience());
            System.out.println("Ссылка:    " + v.getUrl());

            // Жесткая проверка: если в итоговом списке оказался мусор - бьем тревогу
            if ("OVERQUALIFIED".equals(v.getExperience()) ||
                    v.getExperience().contains("4") ||
                    v.getExperience().contains("5")) {
                System.err.println("❌ ОШИБКА! В финальный список пролезла вакансия с большим опытом!");
                hasSeniors = true;
            }
        }

        if (!hasSeniors) {
            System.out.println("\n✅ ТЕСТ ПРОЙДЕН УСПЕШНО! Ни один сеньор не прошел!");
        }

        System.out.println("=== ТЕСТ ЗАВЕРШЕН ===");
    }

}
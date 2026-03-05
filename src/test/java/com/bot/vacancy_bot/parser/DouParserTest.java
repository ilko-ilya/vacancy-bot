package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class DouParserTest {
    @Test
    void testParseVacancies() {
        System.out.println("=== НАЧИНАЕМ ТЕСТ ПАРСЕРА DOU ===");
        long startTime = System.currentTimeMillis();

        // 1. Создаем временный пул потоков специально для теста
        ExecutorService testExecutor = Executors.newFixedThreadPool(5);

        // 2. Инициализируем парсер вручную (без магии Spring)
        DouParser douParser = new DouParser(testExecutor);

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

        // Закрываем тестовый пул
        testExecutor.shutdown();
    }

}
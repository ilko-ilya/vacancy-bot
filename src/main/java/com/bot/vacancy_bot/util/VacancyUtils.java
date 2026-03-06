package com.bot.vacancy_bot.util;

import lombok.NonNull;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VacancyUtils {

    // 1. Идеально: вынесли ключевые слова в Set (O(1) чтение, чистота кода)
    private static final Set<String> IGNORE_KEYWORDS = Set.of(
            "senior", "lead", "head", "architect", "principal", "manager",
            "cto", "director", "vp", "fullstack", "full stack", "full-stack",
            "frontend", "front-end", "android", "ios", "qa", "automation", "test"
    );

    public static boolean shouldIgnore(String titleLower) {
        for (String keyword : IGNORE_KEYWORDS) {
            if (titleLower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // 2. Идеально: ранние возвраты (early return)
    public static @NonNull String getRole(String titleLower) {
        if (titleLower.contains("strong junior")) return "Strong Junior Java Developer";
        if (titleLower.contains("junior")) return "Junior Java Developer";
        if (titleLower.contains("strong middle")) return "Strong Middle Java Developer";
        if (titleLower.contains("middle")) return "Middle Java Developer";
        return "Java Developer";
    }

    // 3. Сохранили твой богатый словарь + нашу новую мощную регулярку
    public static String extractExperience(String text) {
        if (text == null || text.isBlank()) return "Не указан";
        String cleanText = text.replaceAll("<[^>]*>", " ").toLowerCase();

        Pattern pattern = Pattern.compile("(?i)(?:від\\s*|от\\s*|more than\\s*)?(\\d+)\\s*(?:[-–—]\\s*\\d+\\s*)?\\+?\\s*(?:years?|yrs?|рок[іи]в?|року|рік|лет|год[ау]?)");
        Matcher matcher = pattern.matcher(cleanText);

        String validExperience = null;
        int maxValidYears = 0; // <-- НОВЫЙ СЧЕТЧИК: Запоминаем самый большой "разрешенный" опыт

        while (matcher.find()) {
            try {
                int years = Integer.parseInt(matcher.group(1));

                // 🚨 УБИВАЕМ СЕНЬОРОВ: Если нашли 4, 5, 6... сразу бракуем!
                if (years > 3 && years <= 15) {
                    return "OVERQUALIFIED";
                }

                // ✅ ЗАПОМИНАЕМ МАКСИМУМ ДЛЯ ДЖУНОВ/МИДЛОВ:
                // Если мы нашли число от 1 до 3, проверяем, больше ли оно того, что мы уже нашли
                if (years > 0 && years <= 3) {
                    if (years > maxValidYears) {
                        maxValidYears = years; // Обновляем рекорд
                        validExperience = matcher.group(0).trim(); // Запоминаем строку (например, "2+ years")
                    }
                }
            } catch (Exception ignored) {}
        }

        // Возвращаем самое большое найденное подходящее число
        if (validExperience != null) {
            return validExperience;
        }

        // Ищем маркеры стажеров
        Pattern noExpPattern = Pattern.compile("(?iU)\\b(без опыта|без досвіду|no experience|без комерційного|початківець|trainee|intern)\\b");
        if (noExpPattern.matcher(cleanText).find()) {
            return "Без опыта / Минимальный";
        }

        if (cleanText.contains("junior")) return "Без опыта / Junior";

        return "Не указан";
    }

    // 4. Логика работы с датами
    public static boolean isOldVacancy(String dateText) {
        if (dateText == null || dateText.isBlank()) return false;

        String lower = dateText.toLowerCase().trim().replace("\u00a0", " ");

        // Быстрые проверки
        if (lower.contains("тиж") || lower.contains("нед") || lower.contains("week") ||
                lower.contains("міс") || lower.contains("мес") || lower.contains("month") ||
                lower.contains("рік") || lower.contains("год") || lower.contains("лет")) {
            return true;
        }

        // Относительные дни ("5 днів тому")
        if (lower.contains("дн") || lower.contains("дня") || lower.contains("days")) {
            int days = extractFirstNumber(lower);
            return days > 5;
        }

        // Абсолютные даты для DOU ("25 февраля")
        int month = getMonthNumber(lower);
        if (month != -1) {
            int day = extractFirstNumber(lower);
            if (day > 0 && day <= 31) {
                int currentYear = LocalDate.now().getYear();
                try {
                    LocalDate vacancyDate = LocalDate.of(currentYear, month, day);
                    if (vacancyDate.isAfter(LocalDate.now())) {
                        vacancyDate = vacancyDate.minusYears(1);
                    }
                    long daysBetween = ChronoUnit.DAYS.between(vacancyDate, LocalDate.now());
                    return daysBetween > 5;
                } catch (Exception e) {
                    return false;
                }
            }
        }
        return false;
    }

    private static int extractFirstNumber(String text) {
        Matcher matcher = Pattern.compile("\\d+").matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return -1;
    }

    private static int getMonthNumber(String text) {
        if (text.contains("янв") || text.contains("січ") || text.contains("jan")) return 1;
        if (text.contains("фев") || text.contains("лют") || text.contains("feb")) return 2;
        if (text.contains("мар") || text.contains("бер") || text.contains("mar")) return 3;
        if (text.contains("апр") || text.contains("квіт") || text.contains("apr")) return 4;
        if (text.contains("мая") || text.contains("трав") || text.contains("май") || text.contains("may")) return 5;
        if (text.contains("июн") || text.contains("черв") || text.contains("jun")) return 6;
        if (text.contains("июл") || text.contains("лип") || text.contains("jul")) return 7;
        if (text.contains("авг") || text.contains("серп") || text.contains("aug")) return 8;
        if (text.contains("сен") || text.contains("вер") || text.contains("sep")) return 9;
        if (text.contains("окт") || text.contains("жовт") || text.contains("oct")) return 10;
        if (text.contains("ноя") || text.contains("лис") || text.contains("nov")) return 11;
        if (text.contains("дек") || text.contains("груд") || text.contains("dec")) return 12;
        return -1;
    }
}

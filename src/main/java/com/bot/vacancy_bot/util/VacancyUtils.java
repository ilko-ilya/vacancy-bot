package com.bot.vacancy_bot.util;

import lombok.NonNull;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VacancyUtils {

    // Метод проверяет, нужно ли пропустить вакансию (возвращает true, если это мусор)
    public static boolean shouldIgnore(String titleLower) {
        return titleLower.contains("senior") ||
                titleLower.contains("lead") ||
                titleLower.contains("architect") ||
                titleLower.contains("principal") ||
                titleLower.contains("full stack") ||
                titleLower.contains("fullstack") ||
                titleLower.contains("full-stack") ||
                titleLower.contains("front") ||
                titleLower.contains("qa") ||
                titleLower.contains("automation") ||
                titleLower.contains("test");
    }

    // Твой метод для определения грейда
    public static @NonNull String getRole(String titleLower) {
        String role = "Java Developer";

        if (titleLower.contains("strong junior")) {
            role = "Strong Junior Java Developer";
        } else if (titleLower.contains("junior")) {
            role = "Junior Java Developer";
        } else if (titleLower.contains("strong middle")) {
            role = "Strong Middle Java Developer";
        } else if (titleLower.contains("middle")) {
            role = "Middle Java Developer";
        }
        return role;
    }

    // Метод для поиска опыта работы в тексте
    public static String extractExperience(String text) {
        if (text == null || text.isEmpty()) {
            return "Не указан";
        }

        String lower = text.toLowerCase();

        // Проверяем джунские вакансии
        if (lower.contains("без опыта") || lower.contains("без досвіду") || lower.contains("no experience")) {
            return "Без опыта";
        }

        // Ищем фразы: "от 2 лет", "2+ years", "від 1 року", "3-х лет"
        Pattern pattern = Pattern.compile("(?:от|від|from)?\\s*(\\d{1,2})\\s*(?:\\+|\\-х|-ти)?\\s*(?:лет|года|год|років|роки|року|years|year)");
        Matcher matcher = pattern.matcher(lower);

        if (matcher.find()) {
            int years = Integer.parseInt(matcher.group(1));
            // Защита от бреда (например, если случайно спарсился 2024 год)
            if (years > 0 && years <= 15) {
                return "От " + years + " лет";
            }
        }

        return "Не указан";
    }

    // Метод возвращает true, если вакансия слишком старая
    public static boolean isOldVacancy(String dateText) {
        if (dateText == null || dateText.isEmpty()) {
            return false;
        }

        String lower = dateText.toLowerCase();

        // 1. Быстрые отсечения (Work.ua: недели, месяцы, годы)
        if (lower.contains("тиж") || lower.contains("нед") ||
                lower.contains("міс") || lower.contains("мес") ||
                lower.contains("рік") || lower.contains("год") || lower.contains("лет")) {
            return true;
        }

        // 2. Обработка дней для Work.ua ("5 днів тому", "12 дней назад")
        if (lower.contains("дн") || lower.contains("дня") || lower.contains("днів") || lower.contains("дней")) {
            int days = extractFirstNumber(lower);
            return days > 5; // Если больше 5 дней - старая
        }

        // 3. Обработка абсолютных дат для DOU ("25 февраля", "12 січня")
        int month = getMonthNumber(lower);
        if (month != -1) {
            int day = extractFirstNumber(lower);
            if (day > 0 && day <= 31) {
                int currentYear = LocalDate.now().getYear();
                try {
                    // Собираем дату из того, что спарсили
                    LocalDate vacancyDate = LocalDate.of(currentYear, month, day);

                    // Если вакансия "из будущего" (например, в январе парсим "25 декабря"), значит это прошлый год
                    if (vacancyDate.isAfter(LocalDate.now())) {
                        vacancyDate = vacancyDate.minusYears(1);
                    }

                    // Считаем реальную разницу в днях
                    long daysBetween = ChronoUnit.DAYS.between(vacancyDate, LocalDate.now());
                    return daysBetween > 5;
                } catch (Exception e) {
                    return false; // В случае ошибки парсинга лучше пропустить фильтр, чтобы не потерять вакансию
                }
            }
        }

        // Если это "Вчора", "Сьогодні", "Только что" и т.д. (цифр нет)
        return false;
    }

    // Вспомогательный метод: вытаскивает первое число из строки ("3 дні" -> 3)
    private static int extractFirstNumber(String text) {
        Matcher matcher = Pattern.compile("\\d+").matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return -1; // Если цифр нет
    }

    // Вспомогательный метод: переводит рус/укр название месяца в его номер
    // Вспомогательный метод: переводит рус/укр/англ название месяца в его номер
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

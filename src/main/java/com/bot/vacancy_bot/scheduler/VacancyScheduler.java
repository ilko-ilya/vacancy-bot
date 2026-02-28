package com.bot.vacancy_bot.scheduler;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.parser.VacancyParser;
import com.bot.vacancy_bot.repository.VacancyRepository;
import com.bot.vacancy_bot.telegram.VacancyTelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VacancyScheduler {

    private final List<VacancyParser> parsers;
    private final VacancyRepository vacancyRepository;
    private final VacancyTelegramBot telegramBot;

    @Value("${telegram.bot.chat-id}")
    private long chatId;

    @Scheduled(fixedDelay = 60000)
    public void searchAndSendVacancies() {
        log.info("Запуск поиска новых вакансий...");

        for (VacancyParser parser : parsers) {
            List<Vacancy> vacancies = parser.parseVacancies();

            for (Vacancy vacancy : vacancies) {
                // Если ссылки нет в базе — значит это новая вакансия
                if (!vacancyRepository.existsByUrl(vacancy.getUrl())) {

                    // 1. Сохраняем в БД, чтобы не отправить повторно
                    vacancyRepository.save(vacancy);

                    // 2. Формируем красивое сообщение с HTML-разметкой
                    String message = String.format(
                            "🔥 <b>Новая вакансия:</b> %s\n" +
                                    "🏢 <b>Компания:</b> %s\n" +
                                    "📍 <b>Локация:</b> %s\n" +
                                    "📍 <b>Уровень:</b> %s\n\n" +
                                    "⏳ <b>Опыт:</b> %s\n\n" +
                                    "📅 <b>Опубликовано:</b> %s\n\n" +
                                    "🔗 <a href='%s'>Посмотреть на %s</a>",
                            vacancy.getTitle(),
                            vacancy.getCompany(),
                            (vacancy.getLocation() != null && !vacancy.getLocation().isEmpty()) ? vacancy.getLocation() : "Уточняйте",
                            vacancy.getRole(),
                            vacancy.getExperience(),
                            vacancy.getPostedDate(),
                            vacancy.getUrl(),
                            vacancy.getSiteName()
                    );

                    // 3. Отправляем тебе в Telegram
                    telegramBot.sendMessage(chatId, message);

                    log.info("Отправлена вакансия: {}", vacancy.getTitle());
                }
            }
        }
    }
}

package com.bot.vacancy_bot.scheduler;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.parser.VacancyParser;
import com.bot.vacancy_bot.repository.VacancyRepository;
import com.bot.vacancy_bot.telegram.VacancyTelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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

    /**
     * Единый график поиска для экономии API кредитов.
     * Запуски: 11:00, 14:00, 17:00
     * Дни: Понедельник-Пятница + Воскресенье (Суббота исключена)
     */
    @Scheduled(cron = "0 0 11,14,17 * * MON-FRI,SUN")
    public void scheduledSearch() {
        log.info("🎯 [Запуск по расписанию] Проверка новых вакансий (Пн-Пт, Вс)...");
        performSearch();
    }

    // Общая логика поиска
    private void performSearch() {
        for (VacancyParser parser : parsers) {
            try {
                List<Vacancy> vacancies = parser.parseVacancies();
                for (Vacancy vacancy : vacancies) {
                    if (!vacancyRepository.existsByUrl(vacancy.getUrl())) {
                        vacancyRepository.save(vacancy);
                        sendTelegramMessage(vacancy);
                        log.info("Отправлена вакансия: {}", vacancy.getTitle());
                    }
                }
            } catch (Exception e) {
                log.error("Ошибка при парсинге в планировщике: {}", e.getMessage());
            }
        }
    }

    private void sendTelegramMessage(Vacancy vacancy) {
        String message = String.format(
                """
                        🔥 <b>Новая вакансия:</b> %s
                        🏢 <b>Компания:</b> %s
                        📍 <b>Локация:</b> %s
                        📍 <b>Уровень:</b> %s
                        ⏳ <b>Опыт:</b> %s
                        
                        📅 <b>Опубликовано:</b> %s
                        
                        🔗 <a href='%s'>Посмотреть на %s</a>""",
                vacancy.getTitle(),
                vacancy.getCompany(),
                (vacancy.getLocation() != null && !vacancy.getLocation().isEmpty()) ? vacancy.getLocation() : "Уточняйте",
                vacancy.getRole(),
                vacancy.getExperience(),
                vacancy.getPostedDate(),
                vacancy.getUrl(),
                vacancy.getSiteName()
        );
        telegramBot.sendMessage(chatId, message);
    }

    // Твой метод очистки остается без изменений
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanUpDatabase() {
        log.info("Запуск автоматической очистки старых вакансий...");
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(15);
        try {
            int deletedCount = vacancyRepository.deleteOldVacancies(cutoffDate);
            log.info("Очистка завершена. Удалено старых вакансий: {}", deletedCount);
        } catch (Exception e) {
            log.error("Ошибка при удалении старых вакансий: {}", e.getMessage());
        }
    }
}
//    @EventListener(ApplicationReadyEvent.class)
//    public void testRunOnStartup() {
//        log.info("🛠️ ТЕСТ: Принудительный запуск парсеров сразу после старта приложения...");
//        performSearch();
//    }
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
     * МЕДЛЕННЫЙ ПОТОК (Только для тяжелых парсеров, например DOU)
     * Запуски: 11:00, 14:00, 17:00 строго с понедельника по пятницу.
     */
    @Scheduled(cron = "0 0 11,14,17 * * MON-FRI")
    public void scheduledHeavySearch() {
        log.info("🐢 [ScraperAPI] Запуск тяжелого парсинга по расписанию...");

        parsers.stream()
                .filter(VacancyParser::isHeavy)
                .forEach(this::executeParser);
    }

    /**
     * БЫСТРЫЙ ПОТОК (Для легких платформ: Robota.ua, Djinni)
     * Запуск: Строго каждые 5 минут, независимо от времени выполнения предыдущего цикла.
     */
    @Scheduled(fixedDelay = 300000)
    public void scheduledFastSearch() {
        log.info("⚡ [Real-time] Проверка новых вакансий на быстрых платформах...");

        parsers.stream()
                .filter(parser -> !parser.isHeavy())
                .forEach(this::executeParser);
    }

    private void executeParser(VacancyParser parser) {
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
            log.error("Ошибка при парсинге в классе {}: {}", parser.getClass().getSimpleName(), e.getMessage());
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

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanUpDatabase() {
        log.info("🧹 Запуск автоматической очистки старых вакансий...");
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
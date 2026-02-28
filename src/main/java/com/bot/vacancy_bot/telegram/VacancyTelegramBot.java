package com.bot.vacancy_bot.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
public class VacancyTelegramBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.username}")
    private String botUsername;

    public VacancyTelegramBot(@Value("${telegram.bot.token}") String botToken) {
        super(botToken);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Проверяем, что нам прислали именно текстовое сообщение
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            log.info("Получено сообщение: '{}' от chatId: {}", messageText, chatId);

            // Обрабатываем команду /start
            if (messageText.equals("/start")) {
                String responseText = "Привет! Я твой личный поисковик вакансий Java.\n\n" +
                        "Твой Chat ID: <b>" + chatId + "</b>\n\n" +
                        "Сохрани этот ID! Он понадобится нам, чтобы я знал, куда присылать новые вакансии.";

                sendMessage(chatId, responseText);
            } else {
                sendMessage(chatId, "Я пока понимаю только команду /start 🤖");
            }
        }
    }

    // Вспомогательный метод для отправки сообщений (потом будем использовать его для вакансий)
    public void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setParseMode("HTML"); // Включаем поддержку HTML, чтобы делать текст жирным

        try {
            execute(message); // Отправляем запрос в Telegram API
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения: {}", e.getMessage());
        }
    }

}

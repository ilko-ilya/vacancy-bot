package com.bot.vacancy_bot.service;

import com.microsoft.playwright.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class PlaywrightService {

    private Playwright playwright;
    private Browser browser;
    private final Object browserLock = new Object();

    @PostConstruct
    public void init() {
        log.info("🚀 Playwright: Инициализация Chromium...");
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of(
                "--disable-blink-features=AutomationControlled",
                "--no-sandbox",                  // Обязательно для серверов Linux
                "--disable-setuid-sandbox",      // Обязательно для серверов Linux
                "--disable-dev-shm-usage"        // Спасает от падений при нехватке памяти
        )));
    }

    @PreDestroy
    public void close() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    public String fetchPage(String url, String userAgent) {
        synchronized (browserLock) {
            long globalStart = System.currentTimeMillis();
            int attempts = 2;

            for (int i = 1; i <= attempts; i++) {
                // Safeguard: если весь цикл Playwright затянулся больше чем на 1.5 минуты
                if (System.currentTimeMillis() - globalStart > 180000) {
                    log.warn("⏱ Playwright: Превышен глобальный лимит времени на запрос");
                    return null;
                }

                try {
                    return doFetch(url, userAgent);
                } catch (Exception e) {
                    log.warn("⚠️ Playwright попытка {} не удалась: {}", i, e.getMessage());
                }
            }
            return null;
        }
    }

    private String doFetch(String url, String userAgent) {
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(userAgent)
                .setViewportSize(1280, 720))) {

            Page page = context.newPage();
            // Наша фишка для экономии памяти сервера:
            page.route("**/*.{png,jpg,jpeg,svg,css,woff,woff2,pdf,zip}", Route::abort);

            page.navigate(url, new Page.NavigateOptions().setTimeout(60000));

            String selector = url.contains("vacancies") ? "li.l-vacancy" : "body";

            try {
                // Ждем вакансии 40 секунд
                page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(40000));
            } catch (Exception e) {
                log.warn("⏱ Вакансии не появились сразу. Проверяем наличие защиты...");
            }

            try {
                // Если мы видим экран Cloudflare
                if (page.title().contains("Just a moment") || page.title().contains("Cloudflare")) {
                    log.info("🛡 Cloudflare думает... Ждем, пока он нас пропустит.");
                    // Пытаемся дождаться селектора еще раз (если Cloudflare сделает редирект)
                    page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(20000));
                }
            } catch (Exception e) {
                // Та самая ошибка "context destroyed". Значит Cloudflare нас перенаправил!
                log.info("🔄 Зафиксирован редирект от Cloudflare! Ждем отрисовку страницы...");
                page.waitForTimeout(5000); // Даем 5 секунд, чтобы DOM-дерево с вакансиями построилось
            }

            return page.content();
        }
    }
}

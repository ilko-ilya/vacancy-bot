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
                .setViewportSize(1920, 1080))) {

            Page page = context.newPage();
            // Даем 60 секунд на загрузку (вместо 20)
            page.navigate(url, new Page.NavigateOptions().setTimeout(60000));

            // Ждем не просто body, а именно вакансии (так надежнее)
            // Если это главная страница DOU — ищем карточки
            String selector = url.contains("vacancies") ? "li.l-vacancy" : "body";
            try {
                page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(30000));
            } catch (Exception e) {
                log.warn("⏱ Не дождались селектора {}, пробуем забрать что есть", selector);
            }

            // Если Cloudflare всё еще висит
            if (page.title().contains("Just a moment")) {
                log.info("🛡 Замечен Cloudflare, ждем еще 30 сек...");
                page.waitForCondition(() -> !page.title().contains("Just a moment"),
                        new Page.WaitForConditionOptions().setTimeout(30000));
            }

            return page.content();
        }
    }
}

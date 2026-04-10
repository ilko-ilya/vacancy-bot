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
                .setArgs(List.of("--disable-blink-features=AutomationControlled")));
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
                if (System.currentTimeMillis() - globalStart > 90000) {
                    log.warn("⏱ Playwright: Превышен глобальный лимит времени на запрос");
                    return null;
                }

                try {
                    return doFetch(url, userAgent);
                } catch (Exception e) {
                    log.debug("⚠️ Playwright retry {}: {}", i, e.getMessage());
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
            // Уменьшили таймаут до 20 сек (Замечание #4)
            page.navigate(url, new Page.NavigateOptions().setTimeout(20000));

            page.waitForSelector("body", new Page.WaitForSelectorOptions().setTimeout(10000));

            if (page.title().contains("Just a moment")) {
                page.waitForCondition(() -> !page.title().contains("Just a moment"),
                        new Page.WaitForConditionOptions().setTimeout(15000));
            }

            return page.content();
        }
    }
}

package com.bot.vacancy_bot.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
public class ScraperApiClient {

    @Value("${scraper.api.key}")
    private String apiKey;

    // Метод возвращает Document (XML/HTML) или null, если всё сломалось
    public Document fetchXmlDocument(String targetUrl) {
        Document doc = null;

        // ПОПЫТКА 1 и 2: Дешевые прокси (premium=false)
        for (int i = 1; i <= 2; i++) {
            try {
                log.info("🌐 [ScraperAPI] Попытка {}/2 (cheap). URL: {}", i, targetUrl);
                doc = Jsoup.connect(buildProxyUrl(targetUrl, false))
                        .timeout(60000) // Уменьшили таймаут до 10 сек
                        .parser(Parser.xmlParser())
                        .get();

                log.info("🟢 [ScraperAPI] Успешно пробились дешевым запросом!");
                return doc; // Если успешно - сразу выходим

            } catch (Exception e) {
                log.warn("🟡 [ScraperAPI] Дешевая попытка {} сорвалась: {}", i, e.getMessage());
            }
        }

        // ПОПЫТКА 3: Эскалация (premium=true)
        if (Objects.isNull(doc)) {
            try {
                log.info("🔥 [ScraperAPI] Включаем эскалацию! Используем премиум-прокси...");
                doc = Jsoup.connect(buildProxyUrl(targetUrl, true))
                        .timeout(15000) // Для премиума можно дать чуть больше времени
                        .parser(Parser.xmlParser())
                        .get();

                log.info("🟠 [ScraperAPI] Успешно пробились через премиум.");
            } catch (Exception e) {
                log.error("❌ [ScraperAPI] Критическая ошибка: премиум тоже отвалился: {}", e.getMessage());
            }
        }

        return doc;
    }

    private String buildProxyUrl(String targetUrl, boolean usePremium) {
        return "http://api.scraperapi.com/?api_key=" + apiKey
                + "&premium=" + usePremium
                + "&url=" + targetUrl;
    }
}

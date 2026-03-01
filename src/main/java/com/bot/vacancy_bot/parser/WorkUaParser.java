package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.util.VacancyUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
public class WorkUaParser implements VacancyParser {

    private static final String WORK_UA_URL = "https://www.work.ua/ru/jobs-remote-java/";

    @Override
    public List<Vacancy> parseVacancies() {
        List<Vacancy> vacancies = new ArrayList<>();
        try {
            Thread.sleep(2000 + new Random().nextInt(3000));
            Document doc = Jsoup.connect(WORK_UA_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7,uk;q=0.6")
                    .header("Connection", "keep-alive")
                    .referrer("https://www.google.com/")
                    .get();

            Elements elements = doc.select("div.card.job-link");

            for (Element element : elements) {
                Element titleElement = element.selectFirst("h2 a");
                if (titleElement == null) continue;

                String title = titleElement.text();
                String titleLower = title.toLowerCase();
                String url = "https://www.work.ua" + titleElement.attr("href");

                if (VacancyUtils.shouldIgnore(titleLower)) {
                    continue;
                }

                Elements mutedElements = element.select(".text-muted");
                String dateText = mutedElements.text();
                if (dateText.trim().isEmpty()) {
                    dateText = "🔥 Горячая / VIP вакансия";
                }

                if (VacancyUtils.isOldVacancy(dateText)) {
                    continue;
                }

                String role = VacancyUtils.getRole(titleLower);
                String experience = VacancyUtils.extractExperience(element.text());

                String company = "Не указана";
                String location = "Уточняйте";

                Element companyElement = element.selectFirst("span.mr-xs b, span.strong-600, span.company-name, img[alt]");
                if (companyElement != null) {
                    company = companyElement.hasAttr("alt") ? companyElement.attr("alt") : companyElement.text();
                }

                Element headerBlock = element.selectFirst(".add-top-xs, .mt-xs");
                if (headerBlock != null) {
                    String blockText = headerBlock.text().replaceAll("\\s+", " ");
                    if (blockText.contains("•")) {
                        location = blockText.substring(blockText.lastIndexOf("•") + 1).trim();
                    } else if (blockText.contains("·")) {
                        location = blockText.substring(blockText.lastIndexOf("·") + 1).trim();
                    } else if (blockText.toLowerCase().contains("дистанційно") || blockText.toLowerCase().contains("удаленно")) {
                        location = "Remote";
                    }
                }

                Vacancy vacancy = Vacancy.builder()
                        .title(title)
                        .company(company)
                        .location(location)
                        .role(role)
                        .experience(experience)
                        .postedDate(dateText)
                        .url(url)
                        .shortDescription("")
                        .siteName(getSiteName())
                        .parsedAt(LocalDateTime.now())
                        .build();

                vacancies.add(vacancy);
            }
        } catch (IOException e) {
            log.error("Ошибка при парсинге Work.ua: {}", e.getMessage());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return vacancies;
    }

    @Override
    public String getSiteName() {
        return "Work.ua";
    }
}
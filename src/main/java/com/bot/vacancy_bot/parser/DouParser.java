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

@Slf4j
@Component
public class DouParser implements VacancyParser {

    private static final String DOU_URL = "https://jobs.dou.ua/vacancies/?category=Java&remote";

    @Override
    public List<Vacancy> parseVacancies() {
        List<Vacancy> vacancies = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(DOU_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .get();

            Elements elements = doc.select("li.l-vacancy");

            for (Element element : elements) {
                Element titleElement = element.selectFirst(".title a.vt");
                if (titleElement == null) continue;

                String title = titleElement.text();
                String url = titleElement.attr("href");
                String titleLower = title.toLowerCase();

                if (VacancyUtils.shouldIgnore(titleLower)) {
                    continue;
                }

                Element dateElement = element.selectFirst(".date");
                String dateText = dateElement != null ? dateElement.text() : "Недавно";

                if (VacancyUtils.isOldVacancy(dateText)) {
                    continue;
                }

                Element companyElement = element.selectFirst("a.company");
                String company = companyElement != null ? companyElement.text() : "Не указана";

                String location = "Уточняйте";
                Element citiesElement = element.selectFirst(".cities");
                if (citiesElement != null) {
                    location = citiesElement.text();
                }

                Element descElement = element.selectFirst(".sh-info");
                String description = descElement != null ? descElement.text() : "";

                String role = VacancyUtils.getRole(titleLower);
                String experience = VacancyUtils.extractExperience(description);

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
            log.error("Ошибка при парсинге DOU: {}", e.getMessage());
        }
        return vacancies;
    }

    @Override
    public String getSiteName() {
        return "DOU";
    }
}
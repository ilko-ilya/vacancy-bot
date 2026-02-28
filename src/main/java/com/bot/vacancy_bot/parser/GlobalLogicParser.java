package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.util.VacancyUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class GlobalLogicParser implements VacancyParser {

    private static final String GL_URL = "https://www.globallogic.com/ua/career-search-page/?keywords=" +
            "java+developer&experience=1-3+years&work_models=remote";

    @Override
    public List<Vacancy> parseVacancies() {
        List<Vacancy> vacancies = new ArrayList<>();
        try {
            // Подключаемся
            Document doc = Jsoup.connect(GL_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Safari/537.36")
                    .get();

            // По твоим скриншотам: ищем все теги <a> с классом "job_box"
            Elements elements = doc.select("a.job_box");

            log.info("🕵️ GlobalLogic: Найдено карточек вакансий = {}", elements.size());

            for (Element element : elements) {
                // Ссылка лежит прямо в атрибуте href самого элемента <a>
                String url = element.attr("href");

                // Заголовок лежит внутри в теге <h4>
                Element titleElement = element.selectFirst("h4");
                if (titleElement == null) continue;

                String title = titleElement.text();
                String titleLower = title.toLowerCase();

                log.info("🔎 GL видит вакансию: {}", title);

                // 1. Фильтр мусора
                if (VacancyUtils.shouldIgnore(titleLower)) {
                    continue;
                }

                // 2. Определение грейда
                String role = VacancyUtils.getRole(titleLower);

                // Дату и опыт на главной странице GL не пишет, ставим заглушки
                String experience = "Не указан (см. на сайте)";
                String postedDate = "Свежая на GlobalLogic";

                // 3. Сборка объекта
                Vacancy vacancy = Vacancy.builder()
                        .title(title)
                        .company("GlobalLogic") // Зашиваем жестко, мы же парсим их корпоративный сайт
                        .role(role)
                        .experience(experience)
                        .postedDate(postedDate)
                        .url(url)
                        .shortDescription("")
                        .siteName(getSiteName())
                        .parsedAt(LocalDateTime.now())
                        .build();

                vacancies.add(vacancy);
            }
        } catch (Exception e) {
            log.error("Ошибка при парсинге GlobalLogic: {}", e.getMessage());
        }
        return vacancies;
    }

    @Override
    public String getSiteName() {
        return "GlobalLogic";
    }
}

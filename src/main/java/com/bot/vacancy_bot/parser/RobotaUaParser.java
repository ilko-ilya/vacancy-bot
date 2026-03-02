package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.util.VacancyUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RobotaUaParser implements VacancyParser {

    private final ObjectMapper mapper;
    private final WebClient robotaClient;

    @Override
    public List<Vacancy> parseVacancies() {
        List<Vacancy> vacancies = new ArrayList<>();
        try {
            String operationName = "getPublishedVacanciesList";

            // 1. ОБЯЗАТЕЛЬНО: добавляем id компании и город в запрос, чтобы не было 404 и null
            String query = "query getPublishedVacanciesList($filter: PublishedVacanciesFilterInput!, $pagination: PublishedVacanciesPaginationInput!) { " +
                    "publishedVacancies(filter: $filter, pagination: $pagination) { " +
                    "items { id title sortDateText description city { name } company { id name } } } }";

            Map<String, Object> payload = new HashMap<>();
            payload.put("operationName", operationName);
            payload.put("query", query);
            payload.put("variables", Map.of(
                    "filter", Map.of(
                            "keywords", "java developer",
                            "scheduleIds", List.of("1", "3"), // Твои любимые фильтры
                            "rubrics", List.of(Map.of("id", "1", "subrubricIds", List.of()))
                    ),
                    "pagination", Map.of("count", 20, "page", 0)
            ));

            String responseBody = robotaClient.post()
                    .uri("/?q=" + operationName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(mapper.writeValueAsString(payload))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode items = mapper.readTree(responseBody).path("data").path("publishedVacancies").path("items");

            for (JsonNode item : items) {
                // 2. Извлекаем ID для «неубиваемой» ссылки
                String vacancyId = item.path("id").asText("");
                String companyId = item.path("company").path("id").asText("");

                String finalUrl = String.format("https://robota.ua/company%s/vacancy%s", companyId, vacancyId);

                String title = item.path("title").asText("");
                if (title.isEmpty() || VacancyUtils.shouldIgnore(title.toLowerCase())) continue;

                // 3. Фильтрация и извлечение данных без null
                String postedDate = item.path("sortDateText").asText("Недавно");
                if (VacancyUtils.isOldVacancy(postedDate)) continue;

                vacancies.add(Vacancy.builder()
                        .title(title)
                        .company(item.path("company").path("name").asText("Компания"))
                        .url(finalUrl) // 🔴 Теперь ссылка откроется без 404!
                        .location(item.path("city").path("name").asText("Киев / Удаленно"))
                        .role(VacancyUtils.getRole(title.toLowerCase()))
                        .experience(VacancyUtils.extractExperience(title + " " + item.path("description").asText("")))
                        .postedDate(postedDate)
                        .siteName(getSiteName())
                        .parsedAt(LocalDateTime.now())
                        .build());
            }
        } catch (Exception e) {
            log.error("Критическая ошибка Robota.ua", e);
        }
        return vacancies;
    }

    @Override
    public String getSiteName() { return "Robota.ua"; }
}

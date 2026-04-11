package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.util.VacancyUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RobotaUaParser implements VacancyParser {

    private static final String OPERATION_NAME = "getPublishedVacanciesList";

    private static final String QUERY = """
            query getPublishedVacanciesList($filter: PublishedVacanciesFilterInput!, $pagination: PublishedVacanciesPaginationInput!) {
                publishedVacancies(filter: $filter, pagination: $pagination) {
                    items {
                        id
                        title
                        sortDateText
                        description
                        city { name }
                        company { id name }
                    }
                }
            }
            """;

    private final ObjectMapper mapper;
    private final WebClient robotaClient;

    @Override
    public List<Vacancy> parseVacancies() {
        try {
            String response = fetchVacancies(); // 🔴 блокировка ТОЛЬКО тут (граница)
            return extractVacancies(response);
        } catch (Exception e) {
            log.error("Ошибка при парсинге Robota.ua", e);
            return List.of();
        }
    }

    private String fetchVacancies() {
        Map<String, Object> payload = buildPayload();

        return robotaClient.post()
                .uri(uriBuilder -> uriBuilder.queryParam("q", OPERATION_NAME).build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toJson(payload))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("Ошибка API Robota.ua: {}", body);
                                    return Mono.error(new RuntimeException("Robota API error"));
                                })
                )
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .doOnError(e -> log.error("Ошибка HTTP запроса", e))
                .block(); // ✅ допустимо — boundary
    }

    private Map<String, Object> buildPayload() {
        return Map.of(
                "operationName", OPERATION_NAME,
                "query", QUERY,
                "variables", Map.of(
                        "filter", Map.of(
                                "keywords", "java developer",
                                "scheduleIds", List.of("1", "3"),
                                "rubrics", List.of(Map.of("id", "1", "subrubricIds", List.of()))
                        ),
                        "pagination", Map.of("count", 20, "page", 0)
                )
        );
    }

    private List<Vacancy> extractVacancies(String responseBody) throws Exception {
        List<Vacancy> result = new ArrayList<>();

        JsonNode items = mapper.readTree(responseBody)
                .path("data")
                .path("publishedVacancies")
                .path("items");

        for (JsonNode item : items) {
            Vacancy vacancy = mapToVacancy(item);
            if (vacancy != null) {
                result.add(vacancy);
            }
        }

        return result;
    }

    private Vacancy mapToVacancy(JsonNode item) {
        String title = item.path("title").asText("");
        if (title.isEmpty() || VacancyUtils.shouldIgnore(title.toLowerCase())) {
            return null;
        }

        String postedDate = item.path("sortDateText").asText("Недавно");
        if (VacancyUtils.isOldVacancy(postedDate)) {
            return null;
        }

        String description = item.path("description").asText("");
        String experience = VacancyUtils.extractExperience(title + " " + description);

        if ("OVERQUALIFIED".equals(experience)) {
            return null;
        }

        return Vacancy.builder()
                .title(title)
                .company(item.path("company").path("name").asText("Компания"))
                .url(buildVacancyUrl(item))
                .location(item.path("city").path("name").asText("Киев / Удаленно"))
                .role(VacancyUtils.getRole(title.toLowerCase()))
                .experience(experience)
                .postedDate(postedDate)
                .siteName(getSiteName())
                .parsedAt(LocalDateTime.now())
                .build();
    }

    private String buildVacancyUrl(JsonNode item) {
        String vacancyId = item.path("id").asText("");
        String companyId = item.path("company").path("id").asText("");
        return String.format("https://robota.ua/company%s/vacancy%s", companyId, vacancyId);
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка сериализации JSON", e);
        }
    }

    @Override
    public String getSiteName() {
        return "Robota.ua";
    }
}

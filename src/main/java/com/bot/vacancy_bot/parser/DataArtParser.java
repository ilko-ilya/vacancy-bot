package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;
import com.bot.vacancy_bot.util.VacancyUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DataArtParser implements VacancyParser {

    // API ссылка с фильтрами по Java (skills=4626) и Украине (countries=10)
    private static final String DATAART_API_URL = "https://www.dataart.team/dataart-team/api/vacancies/filter-fields-page?skills%5B0%5D=4626&countries=10&level%5B0%5D=5&level%5B0%5D=2&level%5B1%5D=5&level%5B2%5D=5&level%5B3%5D=3&page=1&pageSize=0";

    // Jackson ObjectMapper для парсинга JSON
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Vacancy> parseVacancies() {
        List<Vacancy> vacancies = new ArrayList<>();
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DATAART_API_URL))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String jsonResponse = response.body();

            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode items = rootNode.path("vacancies").path("items");

            if (items.isMissingNode() || !items.isArray()) {
                log.warn("🕵️ DataArt: Не найден массив вакансий в JSON");
                return vacancies;
            }

            log.info("🕵️ DataArt API: Найдено вакансий = {}", items.size());

            for (JsonNode item : items) {
                String title = item.path("title").asText("");
                log.info("🔎 DataArt видит через API: {}", title);

                String titleLower = title.toLowerCase();

                // Фильтруем неподходящие вакансии
                if (VacancyUtils.shouldIgnore(titleLower)) {
                    continue;
                }

                String slug = item.path("slug").asText("");
                String url = "https://dataart.team/vacancies/" + slug;
                String role = VacancyUtils.getRole(titleLower);

                String location = "Уточняйте";
                JsonNode locationTags = item.path("locationTags");

                if (locationTags.isArray() && !locationTags.isEmpty()) {
                    List<String> locList = new ArrayList<>();
                    for (JsonNode locNode : locationTags) {
                        String locTitle = locNode.path("title").asText("");
                        if (!locTitle.isEmpty()) {
                            locList.add(locTitle);
                        }
                    }
                    if (!locList.isEmpty()) {
                        location = String.join(", ", locList);
                    }
                } else {
                    // Если городов нет, пробуем достать страну (countriesTags)
                    JsonNode countriesTags = item.path("countriesTags");
                    if (countriesTags.isArray() && !countriesTags.isEmpty()) {
                        location = countriesTags.get(0).path("title").asText("Уточняйте");
                    }
                }

                Vacancy vacancy = Vacancy.builder()
                        .title(title)
                        .company("DataArt")
                        .location(location)
                        .role(role)
                        .experience("Смотреть на сайте")
                        .postedDate("Свежая (DataArt)")
                        .url(url)
                        .shortDescription("")
                        .siteName(getSiteName())
                        .parsedAt(LocalDateTime.now())
                        .build();

                vacancies.add(vacancy);
            }

        } catch (Exception e) {
            log.error("Ошибка при парсинге JSON DataArt: {}", e.getMessage());
        }
        return vacancies;
    }

    @Override
    public String getSiteName() {
        return "DataArt";
    }
}
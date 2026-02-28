package com.bot.vacancy_bot.parser;

import com.bot.vacancy_bot.model.Vacancy;

import java.util.List;

public interface VacancyParser {

    List<Vacancy> parseVacancies();

    String getSiteName();

}

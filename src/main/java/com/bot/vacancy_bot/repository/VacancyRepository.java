package com.bot.vacancy_bot.repository;

import com.bot.vacancy_bot.model.Vacancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

    boolean existsByUrl(String url);

    @Transactional
    @Modifying
    @Query("DELETE FROM Vacancy v WHERE v.parsedAt < :date")
    int deleteOldVacancies(@Param("date") LocalDateTime date);
}

package com.bot.vacancy_bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;

@Configuration
public class AsyncConfig {

    @Bean(name = "douParserExecutor")
    public ExecutorService douParserExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // Сколько потоков живет всегда
        executor.setMaxPoolSize(5);  // Максимум потоков
        executor.setQueueCapacity(100); // Очередь для задач, если все потоки заняты
        executor.setThreadNamePrefix("DouParser-");
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }
}

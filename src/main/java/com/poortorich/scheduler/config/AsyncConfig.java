package com.poortorich.scheduler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Value("${ranking.task-executor.core-pool-size}")
    private int corePoolSize;

    @Value("${ranking.task-executor.max-pool-size}")
    private int maxPoolSize;

    @Value("${ranking.task-executor.queue-capacity}")
    private int queueCapacity;

    @Bean(name = "rankingTaskExecutor")
    public TaskExecutor rankingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ranking-batch-");
        executor.initialize();
        return executor;
    }
}

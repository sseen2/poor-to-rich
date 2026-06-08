package com.poortorich.scheduler.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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

    @Bean(name = "chatroomSummaryTaskExecutor")
    public TaskExecutor chatroomSummaryTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("chatroom-summary-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "chatMessagePostTaskExecutor")
    public ThreadPoolTaskExecutor chatMessagePostTaskExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("chat-message-post-");
        executor.initialize();

        Gauge.builder("chat.message.post.executor.queue.size", executor,
                        taskExecutor -> taskExecutor.getThreadPoolExecutor().getQueue().size())
                .description("Queued chat message post-processing tasks")
                .register(meterRegistry);
        Gauge.builder("chat.message.post.executor.active.count", executor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Active chat message post-processing executor threads")
                .register(meterRegistry);

        return executor;
    }

}

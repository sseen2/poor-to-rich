package com.poortorich.websocket.config;

//import io.micrometer.core.instrument.Gauge;
//import io.micrometer.core.instrument.MeterRegistry;
//import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
//import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
//import org.springframework.scheduling.TaskScheduler;
//import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
//import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

//    private final MeterRegistry meterRegistry;
//
//    public WebSocketConfig(MeterRegistry meterRegistry) {
//        this.meterRegistry = meterRegistry;
//    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/chat-websocket")
                .setAllowedOrigins("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub");
//                .setHeartbeatValue(new long[]{30_000, 30_000})
//                .setTaskScheduler(heartbeatScheduler());

        registry.setApplicationDestinationPrefixes("/pub");
        registry.setUserDestinationPrefix("/chat/user");
    }

//    @Override
//    public void configureClientOutboundChannel(ChannelRegistration registration) {
//        registration.taskExecutor(websocketOutboundTaskExecutor());
//    }
//
//    @Bean(destroyMethod = "shutdown")
//    public ThreadPoolTaskExecutor websocketOutboundTaskExecutor() {
//        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//        executor.setCorePoolSize(3);
//        executor.setMaxPoolSize(3);
//        executor.setQueueCapacity(200);
//        executor.setThreadNamePrefix("wss-outbound-");
//        executor.initialize();
//
//        Gauge.builder("websocket.outbound.executor.queue.size", executor,
//                        taskExecutor -> taskExecutor.getThreadPoolExecutor().getQueue().size())
//                .description("Queued WebSocket outbound messages")
//                .register(meterRegistry);
//        Gauge.builder("websocket.outbound.executor.active.count", executor, ThreadPoolTaskExecutor::getActiveCount)
//                .description("Active WebSocket outbound executor threads")
//                .register(meterRegistry);
//        Gauge.builder("websocket.outbound.executor.pool.size", executor, ThreadPoolTaskExecutor::getPoolSize)
//                .description("Current WebSocket outbound executor pool size")
//                .register(meterRegistry);
//        Gauge.builder("websocket.outbound.executor.completed.task.count", executor,
//                        taskExecutor -> taskExecutor.getThreadPoolExecutor().getCompletedTaskCount())
//                .description("Completed WebSocket outbound executor task count")
//                .register(meterRegistry);
//
//        return executor;
//    }
//
//    @Bean(destroyMethod = "shutdown")
//    public TaskScheduler heartbeatScheduler() {
//        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
//        scheduler.setPoolSize(1);
//        scheduler.setThreadNamePrefix("wss-heartbeat-");
//        scheduler.setWaitForTasksToCompleteOnShutdown(true);
//        scheduler.setAwaitTerminationSeconds(40);
//        scheduler.setRemoveOnCancelPolicy(true);
//        return scheduler;
//    }
}

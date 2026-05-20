package com.poortorich.ranking;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.repository.ChatroomRepository;
import com.poortorich.ranking.schedules.RankingScheduler;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("performance")
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "spring.jpa.properties.hibernate.use_sql_comments=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class RankingSchedulerExecutionTimeTest {

    @Autowired
    private RankingScheduler rankingScheduler;

    @Autowired
    @Qualifier("rankingTaskExecutor")
    private ThreadPoolTaskExecutor rankingTaskExecutor;

    @Autowired
    private ChatroomRepository chatroomRepository;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("랭킹 스케줄러 실행 시간 및 Hikari 커넥션 지표 측정")
    void measureRankingSchedulerExecutionTimeAndHikariMetrics() throws InterruptedException {
        List<Chatroom> targetChatrooms = chatroomRepository.findAllByIsRankingEnabledIsTrueAndIsClosedIsFalse();

        assertThat(targetChatrooms)
                .as("랭킹 기능이 활성화된 운영 중 채팅방 테스트 데이터가 필요합니다.")
                .isNotEmpty();

        HikariPoolMXBean hikariPool = getHikariPool();
        HikariMetrics metrics = new HikariMetrics();
        AtomicBoolean sampling = new AtomicBoolean(true);
        Thread sampler = startHikariMetricsSampler(hikariPool, metrics, sampling);

        long startedAt = System.nanoTime();
        rankingScheduler.calculateAndBroadcastWeeklyRanking();
        waitUntilAsyncRankingTasksDone();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        sampling.set(false);
        sampler.join(1_000);

        printResult(targetChatrooms.size(), elapsedMillis, metrics);
    }

    private HikariPoolMXBean getHikariPool() {
        assertThat(dataSource)
                .as("Hikari 커넥션 지표 측정을 위해 DataSource는 HikariDataSource여야 합니다.")
                .isInstanceOf(HikariDataSource.class);

        return ((HikariDataSource) dataSource).getHikariPoolMXBean();
    }

    private Thread startHikariMetricsSampler(
            HikariPoolMXBean hikariPool,
            HikariMetrics metrics,
            AtomicBoolean sampling
    ) {
        Thread sampler = new Thread(() -> {
            while (sampling.get()) {
                metrics.record(
                        hikariPool.getActiveConnections(),
                        hikariPool.getIdleConnections(),
                        hikariPool.getTotalConnections(),
                        hikariPool.getThreadsAwaitingConnection(),
                        rankingTaskExecutor.getActiveCount(),
                        rankingTaskExecutor.getQueueSize()
                );

                try {
                    Thread.sleep(100);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "ranking-hikari-metrics-sampler");
        sampler.setDaemon(true);
        sampler.start();
        return sampler;
    }

    private void waitUntilAsyncRankingTasksDone() throws InterruptedException {
        int stableEmptyCount = 0;
        long timeoutAt = System.nanoTime() + Duration.ofMinutes(10).toNanos();

        while (System.nanoTime() < timeoutAt) {
            boolean executorEmpty = rankingTaskExecutor.getActiveCount() == 0
                    && rankingTaskExecutor.getQueueSize() == 0;

            if (executorEmpty) {
                stableEmptyCount++;
                if (stableEmptyCount >= 3) {
                    return;
                }
            } else {
                stableEmptyCount = 0;
            }

            Thread.sleep(100);
        }

        throw new IllegalStateException("랭킹 스케줄러 비동기 작업이 제한 시간 안에 끝나지 않았습니다.");
    }

    private void printResult(int targetChatroomCount, long elapsedMillis, HikariMetrics metrics) {
        System.out.println();
        System.out.println("============================================");
        System.out.println("     랭킹 스케줄러 실행 시간 및 커넥션 지표     ");
        System.out.println("============================================");
        System.out.printf("측정 대상 채팅방 수:       %,d개%n", targetChatroomCount);
        System.out.printf("총 실행 시간:             %,dms%n", elapsedMillis);
        System.out.printf("채팅방당 평균 처리 시간:   %.3fms%n", elapsedMillis / (double) targetChatroomCount);
        System.out.println("--------------------------------------------");
        System.out.printf("샘플링 횟수:              %,d회%n", metrics.sampleCount());
        System.out.printf("Hikari active 최대:       %,d개%n", metrics.maxActiveConnections());
        System.out.printf("Hikari idle 최소:         %,d개%n", metrics.minIdleConnections());
        System.out.printf("Hikari total 최대:        %,d개%n", metrics.maxTotalConnections());
        System.out.printf("Hikari pending 최대:      %,d개%n", metrics.maxPendingThreads());
        System.out.printf("Executor active 최대:     %,d개%n", metrics.maxExecutorActiveThreads());
        System.out.printf("Executor queue 최대:      %,d개%n", metrics.maxExecutorQueueSize());
        System.out.println("============================================");
        System.out.println();
    }

    private static class HikariMetrics {

        private final AtomicInteger sampleCount = new AtomicInteger();
        private final AtomicInteger maxActiveConnections = new AtomicInteger();
        private final AtomicInteger minIdleConnections = new AtomicInteger(Integer.MAX_VALUE);
        private final AtomicInteger maxTotalConnections = new AtomicInteger();
        private final AtomicInteger maxPendingThreads = new AtomicInteger();
        private final AtomicInteger maxExecutorActiveThreads = new AtomicInteger();
        private final AtomicInteger maxExecutorQueueSize = new AtomicInteger();

        private void record(
                int activeConnections,
                int idleConnections,
                int totalConnections,
                int pendingThreads,
                int executorActiveThreads,
                int executorQueueSize
        ) {
            sampleCount.incrementAndGet();
            updateMax(maxActiveConnections, activeConnections);
            updateMin(minIdleConnections, idleConnections);
            updateMax(maxTotalConnections, totalConnections);
            updateMax(maxPendingThreads, pendingThreads);
            updateMax(maxExecutorActiveThreads, executorActiveThreads);
            updateMax(maxExecutorQueueSize, executorQueueSize);
        }

        private int sampleCount() {
            return sampleCount.get();
        }

        private int maxActiveConnections() {
            return maxActiveConnections.get();
        }

        private int minIdleConnections() {
            int value = minIdleConnections.get();
            return value == Integer.MAX_VALUE ? 0 : value;
        }

        private int maxTotalConnections() {
            return maxTotalConnections.get();
        }

        private int maxPendingThreads() {
            return maxPendingThreads.get();
        }

        private int maxExecutorActiveThreads() {
            return maxExecutorActiveThreads.get();
        }

        private int maxExecutorQueueSize() {
            return maxExecutorQueueSize.get();
        }

        private void updateMax(AtomicInteger target, int value) {
            target.accumulateAndGet(value, Math::max);
        }

        private void updateMin(AtomicInteger target, int value) {
            target.accumulateAndGet(value, Math::min);
        }
    }
}

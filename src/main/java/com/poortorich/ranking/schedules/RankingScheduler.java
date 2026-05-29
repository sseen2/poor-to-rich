package com.poortorich.ranking.schedules;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.service.ChatroomService;
import com.poortorich.ranking.facade.RankingFacade;
import com.poortorich.ranking.model.BatchRankingResult;
import com.poortorich.websocket.stomp.command.subscribe.endpoint.SubscribeEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
public class RankingScheduler {

    private final RankingFacade rankingFacade;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatroomService chatroomService;
    private final TaskExecutor taskExecutor;
    private final int batchSize;
    private final int taskExecutorMaxPoolSize;

    public RankingScheduler(
            RankingFacade rankingFacade,
            SimpMessagingTemplate messagingTemplate,
            ChatroomService chatroomService,
            @Qualifier("rankingTaskExecutor") TaskExecutor taskExecutor,
            @Value("${ranking.scheduler.batch-size}") int batchSize,
            @Value("${ranking.task-executor.max-pool-size}") int taskExecutorMaxPoolSize
    ) {
        this.rankingFacade = rankingFacade;
        this.messagingTemplate = messagingTemplate;
        this.chatroomService = chatroomService;
        this.taskExecutor = taskExecutor;
        this.batchSize = batchSize;
        this.taskExecutorMaxPoolSize = taskExecutorMaxPoolSize;
    }

    // 랭킹 집계 스케줄러
    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    public void calculateAndBroadcastWeeklyRanking() {
        long startedAt = System.nanoTime();

        // 랭킹 기능을 활성화한 운영 중인 채팅방 목록 조회
        List<Chatroom> activeChatrooms = chatroomService.getChatroomsByRankingEnabledIsTrue();

        AtomicInteger counter = new AtomicInteger();
        // 조회한 채팅방 목록을 설정된 배치 크기 단위로 쪼개기
        List<List<Chatroom>> batches = activeChatrooms.stream()
                .collect(Collectors.groupingBy(chatroom -> counter.getAndIncrement() / batchSize))
                .values()
                .stream()
                .toList();

        if (batches.isEmpty()) {
            log.info("랭킹 집계 스케줄러 종료: 처리할 채팅방이 없습니다.");
            return;
        }

        int workerCount = Math.min(taskExecutorMaxPoolSize, batches.size());
        AtomicInteger nextBatchIndex = new AtomicInteger();
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicLong skippedCount = new AtomicLong();

        log.info(
                "랭킹 집계 스케줄러 시작: chatroomCount={}, batchSize={}, batchCount={}, workerCount={}",
                activeChatrooms.size(),
                batchSize,
                batches.size(),
                workerCount
        );

        // executor 큐에 전체 배치를 한 번에 넣지 않고, worker가 다음 배치를 하나씩 가져가며 처리한다.
        List<CompletableFuture<Void>> futures = IntStream.rangeClosed(1, workerCount)
                .mapToObj(workerIndex -> CompletableFuture.runAsync(
                        () -> processBatches(workerIndex, batches, nextBatchIndex, successCount, failureCount, skippedCount),
                        taskExecutor
                ))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, exception) -> {
                    if (Objects.nonNull(exception)) {
                        log.error(
                                "랭킹 집계 스케줄러 비동기 작업 종료 중 예외 발생: exceptionType={}, message={}",
                                exception.getClass().getSimpleName(),
                                exception.getMessage(),
                                exception
                        );
                    }

                    log.info(
                            "랭킹 집계 스케줄러 전체 종료: chatroomCount={}, batchCount={}, workerCount={}, success={}, skipped={}, failure={}, elapsedMs={}",
                            activeChatrooms.size(),
                            batches.size(),
                            workerCount,
                            successCount.get(),
                            skippedCount.get(),
                            failureCount.get(),
                            elapsedMillis(startedAt)
                    );
                });

        log.info(
                "랭킹 집계 스케줄러 작업 제출 완료: batchCount={}, workerCount={}, elapsedMs={}",
                batches.size(),
                workerCount,
                elapsedMillis(startedAt)
        );
    }

    private void processBatches(
            int workerIndex,
            List<List<Chatroom>> batches,
            AtomicInteger nextBatchIndex,
            AtomicInteger successCount,
            AtomicInteger failureCount,
            AtomicLong skippedCount
    ) {
        while (true) {
            int currentBatchIndex = nextBatchIndex.getAndIncrement();
            if (currentBatchIndex >= batches.size()) {
                log.info("랭킹 집계 worker 종료: workerIndex={}", workerIndex);
                return;
            }

            processBatch(
                    currentBatchIndex + 1,
                    batches.get(currentBatchIndex),
                    successCount,
                    failureCount,
                    skippedCount
            );
        }
    }

    private void processBatch(
            int batchIndex,
            List<Chatroom> chatrooms,
            AtomicInteger successCount,
            AtomicInteger failureCount,
            AtomicLong skippedCount
    ) {
        long batchStartedAt = System.nanoTime();
        AtomicInteger batchSuccessCount = new AtomicInteger();
        AtomicInteger batchFailureCount = new AtomicInteger();
        AtomicInteger batchSkippedCount = new AtomicInteger();

        log.info(
                "랭킹 집계 배치 시작: batchIndex={}, batchChatroomCount={}",
                batchIndex,
                chatrooms.size()
        );

        try {
            List<BatchRankingResult> rankingResults = rankingFacade.calculateRankings(chatrooms);
            rankingResults.forEach(result -> {
                messagingTemplate.convertAndSend(
                        SubscribeEndpoint.CHATROOM_SUBSCRIBE_PREFIX + result.chatroom().getId(),
                        result.payload().mapToBasePayload()
                );
            });

            batchSuccessCount.addAndGet(rankingResults.size());
            batchSkippedCount.addAndGet(chatrooms.size() - rankingResults.size());
        } catch (Exception exception) {
            batchFailureCount.addAndGet(chatrooms.size());
            log.error(
                    "랭킹 집계 배치 실패: batchIndex={}, batchChatroomCount={}, exceptionType={}, message={}",
                    batchIndex,
                    chatrooms.size(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    exception
            );
        }

        successCount.addAndGet(batchSuccessCount.get());
        failureCount.addAndGet(batchFailureCount.get());
        skippedCount.addAndGet(batchSkippedCount.get());

        log.info(
                "랭킹 집계 배치 종료: batchIndex={}, success={}, skipped={}, failure={}, elapsedMs={}",
                batchIndex,
                batchSuccessCount.get(),
                batchSkippedCount.get(),
                batchFailureCount.get(),
                elapsedMillis(batchStartedAt)
        );
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

package com.poortorich.ranking.schedules;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.global.exceptions.ConflictException;
import com.poortorich.chat.service.ChatroomService;
import com.poortorich.ranking.facade.RankingFacade;
import com.poortorich.ranking.model.BatchRankingResult;
import com.poortorich.ranking.response.enums.RankingResponse;
import com.poortorich.websocket.stomp.command.subscribe.endpoint.SubscribeEndpoint;
import com.poortorich.websocket.stomp.service.SubscribeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RankingScheduler {

    private final RankingFacade rankingFacade;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatroomService chatroomService;
    private final SubscribeService subscribeService;
    private final TaskExecutor taskExecutor;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RankingScheduler(
            RankingFacade rankingFacade,
            SimpMessagingTemplate messagingTemplate,
            ChatroomService chatroomService,
            SubscribeService subscribeService,
            @Qualifier("rankingTaskExecutor") TaskExecutor taskExecutor,
            @Value("${ranking.scheduler.batch-size}") int batchSize
    ) {
        this.rankingFacade = rankingFacade;
        this.messagingTemplate = messagingTemplate;
        this.chatroomService = chatroomService;
        this.subscribeService = subscribeService;
        this.taskExecutor = taskExecutor;
        this.batchSize = batchSize;
    }

    // 랭킹 집계 스케줄러
    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    public void calculateAndBroadcastWeeklyRanking() {
        startRankingExecution();

        long startedAt = System.currentTimeMillis();
        try {
            // 랭킹 기능을 활성화한 운영 중인 채팅방 목록 조회
            List<Chatroom> activeChatrooms = chatroomService.getChatroomsByRankingEnabledIsTrue();

            AtomicInteger counter = new AtomicInteger();
            // 조회한 채팅방 목록을 설정된 배치 크기 단위로 쪼개기
            Collection<List<Chatroom>> batches = activeChatrooms.stream()
                    .collect(Collectors.groupingBy(chatroom -> counter.getAndIncrement() / batchSize))
                    .values();

//        processBatch(activeChatrooms);

//         하나의 배치를 하나의 스레드에서 비동기로 처리
            List<CompletableFuture<RankingBatchSummary>> futures = batches.stream()
                    .map(batch -> CompletableFuture.supplyAsync(() -> processBatch(batch), taskExecutor)
                            .exceptionally(exception -> {
                                log.error("랭킹 집계 batch 실패 - chatroomCount: {}", batch.size(), exception);
                                return RankingBatchSummary.failed(batch.size());
                            }))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .whenComplete((ignored, exception) -> {
                        try {
                            logRankingSummary(
                                    activeChatrooms.size(),
                                    batches.size(),
                                    futures,
                                    startedAt
                            );
                        } finally {
                            running.set(false);
                        }
                    });
        } catch (RuntimeException exception) {
            running.set(false);
            throw exception;
        }
    }

    public void calculateAndBroadcastWeeklyRanking(Long chatroomId) {
        startRankingExecution();

        long startedAt = System.currentTimeMillis();
        try {
            Chatroom chatroom = chatroomService.findById(chatroomId);

            RankingBatchSummary summary = processBatch(List.of(chatroom));

            log.info(
                    "랭킹 단일 집계 완료 - chatroomId: {}, calculatedRankings: {}, savedMessages: {}, broadcastMessages: {}, skippedBroadcastMessages: {}, failedBatches: {}, failedChatrooms: {}, elapsedMs: {}",
                    chatroomId,
                    summary.calculatedRankingCount(),
                    summary.savedMessageCount(),
                    summary.broadcastMessageCount(),
                    summary.skippedBroadcastMessageCount(),
                    summary.failedBatchCount(),
                    summary.failedChatroomCount(),
                    System.currentTimeMillis() - startedAt
            );
        } finally {
            running.set(false);
        }
    }

    private void startRankingExecution() {
        if (!running.compareAndSet(false, true)) {
            log.warn("랭킹 스케줄러 실행 요청 무시 - 이미 실행 중입니다.");
            throw new ConflictException(RankingResponse.RANKING_SCHEDULER_ALREADY_RUNNING);
        }
    }

    private RankingBatchSummary processBatch(List<Chatroom> chatrooms) {
        List<BatchRankingResult> rankingResults = rankingFacade.calculateRankings(chatrooms);
        Set<Long> subscribedChatroomIds = subscribeService.findSubscribedChatroomIds(rankingResults.stream()
                .map(result -> result.chatroom().getId())
                .toList());

        int broadcastMessageCount = 0;
        int skippedBroadcastMessageCount = 0;
        for (BatchRankingResult result : rankingResults) {
            Long chatroomId = result.chatroom().getId();
            if (!subscribedChatroomIds.contains(chatroomId)) {
                skippedBroadcastMessageCount++;
                continue;
            }

            messagingTemplate.convertAndSend(
                    SubscribeEndpoint.CHATROOM_SUBSCRIBE_PREFIX + chatroomId,
                    result.payload().mapToBasePayload()
            );
            broadcastMessageCount++;
        }

        return RankingBatchSummary.success(
                chatrooms.size(),
                rankingResults.size(),
                rankingResults.size(),
                broadcastMessageCount,
                skippedBroadcastMessageCount
        );
    }

    private void logRankingSummary(
            int totalChatroomCount,
            int batchCount,
            List<CompletableFuture<RankingBatchSummary>> futures,
            long startedAt
    ) {
        List<RankingBatchSummary> summaries = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        int calculatedRankingCount = summaries.stream()
                .mapToInt(RankingBatchSummary::calculatedRankingCount)
                .sum();
        int processedChatroomCount = summaries.stream()
                .mapToInt(RankingBatchSummary::scannedChatroomCount)
                .sum();
        int savedMessageCount = summaries.stream()
                .mapToInt(RankingBatchSummary::savedMessageCount)
                .sum();
        int broadcastMessageCount = summaries.stream()
                .mapToInt(RankingBatchSummary::broadcastMessageCount)
                .sum();
        int skippedBroadcastMessageCount = summaries.stream()
                .mapToInt(RankingBatchSummary::skippedBroadcastMessageCount)
                .sum();
        int failedBatchCount = summaries.stream()
                .mapToInt(RankingBatchSummary::failedBatchCount)
                .sum();
        int failedChatroomCount = summaries.stream()
                .mapToInt(RankingBatchSummary::failedChatroomCount)
                .sum();

        log.info(
                "랭킹 집계 완료 - targetChatrooms: {}, processedChatrooms: {}, batches: {}, calculatedRankings: {}, savedMessages: {}, broadcastMessages: {}, skippedBroadcastMessages: {}, failedBatches: {}, failedChatrooms: {}, elapsedMs: {}",
                totalChatroomCount,
                processedChatroomCount,
                batchCount,
                calculatedRankingCount,
                savedMessageCount,
                broadcastMessageCount,
                skippedBroadcastMessageCount,
                failedBatchCount,
                failedChatroomCount,
                System.currentTimeMillis() - startedAt
        );
    }

    private record RankingBatchSummary(
            int scannedChatroomCount,
            int calculatedRankingCount,
            int savedMessageCount,
            int broadcastMessageCount,
            int skippedBroadcastMessageCount,
            int failedBatchCount,
            int failedChatroomCount
    ) {
        private static RankingBatchSummary success(
                int scannedChatroomCount,
                int calculatedRankingCount,
                int savedMessageCount,
                int broadcastMessageCount,
                int skippedBroadcastMessageCount
        ) {
            return new RankingBatchSummary(
                    scannedChatroomCount,
                    calculatedRankingCount,
                    savedMessageCount,
                    broadcastMessageCount,
                    skippedBroadcastMessageCount,
                    0,
                    0
            );
        }

        private static RankingBatchSummary failed(int failedChatroomCount) {
            return new RankingBatchSummary(0, 0, 0, 0, 0, 1, failedChatroomCount);
        }
    }
}

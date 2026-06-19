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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RankingScheduler {

    private final RankingFacade rankingFacade;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatroomService chatroomService;
    private final TaskExecutor taskExecutor;
    private final int batchSize;

    public RankingScheduler(
            RankingFacade rankingFacade,
            SimpMessagingTemplate messagingTemplate,
            ChatroomService chatroomService,
            @Qualifier("rankingTaskExecutor") TaskExecutor taskExecutor,
            @Value("${ranking.scheduler.batch-size}") int batchSize
    ) {
        this.rankingFacade = rankingFacade;
        this.messagingTemplate = messagingTemplate;
        this.chatroomService = chatroomService;
        this.taskExecutor = taskExecutor;
        this.batchSize = batchSize;
    }

    // 랭킹 집계 스케줄러
    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    public void calculateAndBroadcastWeeklyRanking() {
        long startedAt = System.currentTimeMillis();
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
                .whenComplete((ignored, exception) -> logRankingSummary(
                        activeChatrooms.size(),
                        batches.size(),
                        futures,
                        startedAt
                ));
    }

    public void calculateAndBroadcastWeeklyRanking(Long chatroomId) {
        long startedAt = System.currentTimeMillis();
        Chatroom chatroom = chatroomService.findById(chatroomId);

        RankingBatchSummary summary = processBatch(List.of(chatroom));

        log.info(
                "랭킹 단일 집계 완료 - chatroomId: {}, calculatedRankings: {}, broadcastMessages: {}, failedBatches: {}, failedChatrooms: {}, elapsedMs: {}",
                chatroomId,
                summary.calculatedRankingCount(),
                summary.broadcastMessageCount(),
                summary.failedBatchCount(),
                summary.failedChatroomCount(),
                System.currentTimeMillis() - startedAt
        );
    }

    private RankingBatchSummary processBatch(List<Chatroom> chatrooms) {
        List<BatchRankingResult> rankingResults = rankingFacade.calculateRankings(chatrooms);

        rankingResults.forEach(result -> {
            // 채팅방에 랭킹 데이터를 담아 메세지 전송
            messagingTemplate.convertAndSend(
                    SubscribeEndpoint.CHATROOM_SUBSCRIBE_PREFIX + result.chatroom().getId(),
                    result.payload().mapToBasePayload()
            );
        });

        return RankingBatchSummary.success(chatrooms.size(), rankingResults.size(), rankingResults.size());
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
        int broadcastMessageCount = summaries.stream()
                .mapToInt(RankingBatchSummary::broadcastMessageCount)
                .sum();
        int failedBatchCount = summaries.stream()
                .mapToInt(RankingBatchSummary::failedBatchCount)
                .sum();
        int failedChatroomCount = summaries.stream()
                .mapToInt(RankingBatchSummary::failedChatroomCount)
                .sum();

        log.info(
                "랭킹 집계 완료 - targetChatrooms: {}, processedChatrooms: {}, batches: {}, calculatedRankings: {}, broadcastMessages: {}, failedBatches: {}, failedChatrooms: {}, elapsedMs: {}",
                totalChatroomCount,
                processedChatroomCount,
                batchCount,
                calculatedRankingCount,
                broadcastMessageCount,
                failedBatchCount,
                failedChatroomCount,
                System.currentTimeMillis() - startedAt
        );
    }

    private record RankingBatchSummary(
            int scannedChatroomCount,
            int calculatedRankingCount,
            int broadcastMessageCount,
            int failedBatchCount,
            int failedChatroomCount
    ) {
        private static RankingBatchSummary success(
                int scannedChatroomCount,
                int calculatedRankingCount,
                int broadcastMessageCount
        ) {
            return new RankingBatchSummary(scannedChatroomCount, calculatedRankingCount, broadcastMessageCount, 0, 0);
        }

        private static RankingBatchSummary failed(int failedChatroomCount) {
            return new RankingBatchSummary(0, 0, 0, 1, failedChatroomCount);
        }
    }
}

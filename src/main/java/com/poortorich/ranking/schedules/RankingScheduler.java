package com.poortorich.ranking.schedules;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.service.ChatroomService;
import com.poortorich.ranking.facade.RankingFacade;
import com.poortorich.ranking.payload.response.RankingResponsePayload;
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

        log.info(
                "랭킹 집계 스케줄러 시작: chatroomCount={}, batchSize={}, batchCount={}",
                activeChatrooms.size(),
                batchSize,
                batches.size()
        );

        if (batches.isEmpty()) {
            log.info("랭킹 집계 스케줄러 종료: 처리할 채팅방이 없습니다.");
            return;
        }

        AtomicInteger batchIndex = new AtomicInteger();
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicLong skippedCount = new AtomicLong();

        // 하나의 배치를 하나의 스레드에서 비동기로 처리
        List<CompletableFuture<Void>> futures = batches.stream()
                .map(batch -> {
                    int currentBatchIndex = batchIndex.incrementAndGet();
                    return CompletableFuture.runAsync(
                            () -> processBatch(currentBatchIndex, batch, successCount, failureCount, skippedCount),
                            taskExecutor
                    );
                })
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
                            "랭킹 집계 스케줄러 전체 종료: chatroomCount={}, batchCount={}, success={}, skipped={}, failure={}, elapsedMs={}",
                            activeChatrooms.size(),
                            batches.size(),
                            successCount.get(),
                            skippedCount.get(),
                            failureCount.get(),
                            elapsedMillis(startedAt)
                    );
                });

        log.info(
                "랭킹 집계 스케줄러 작업 제출 완료: batchCount={}, elapsedMs={}",
                batches.size(),
                elapsedMillis(startedAt)
        );
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

        chatrooms.forEach(chatroom -> processChatroom(
                batchIndex,
                chatroom,
                batchSuccessCount,
                batchFailureCount,
                batchSkippedCount
        ));

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

    private void processChatroom(
            int batchIndex,
            Chatroom chatroom,
            AtomicInteger batchSuccessCount,
            AtomicInteger batchFailureCount,
            AtomicInteger batchSkippedCount
    ) {
        try {
            RankingResponsePayload payload = rankingFacade.calculateRanking(chatroom);

            if (!Objects.isNull(payload)) {
                // 채팅방에 랭킹 데이터를 담아 메세지 전송
                messagingTemplate.convertAndSend(
                        SubscribeEndpoint.CHATROOM_SUBSCRIBE_PREFIX + chatroom.getId(),
                        payload.mapToBasePayload()
                );
                batchSuccessCount.incrementAndGet();
                return;
            }

            batchSkippedCount.incrementAndGet();
            log.info(
                    "랭킹 집계 건너뜀: batchIndex={}, chatroomId={}, reason=NOT_ENOUGH_RANKABLE_PARTICIPANTS",
                    batchIndex,
                    chatroom.getId()
            );
        } catch (Exception exception) {
            batchFailureCount.incrementAndGet();
            log.error(
                    "랭킹 집계 실패: batchIndex={}, chatroomId={}, exceptionType={}, message={}",
                    batchIndex,
                    chatroom.getId(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    exception
            );
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

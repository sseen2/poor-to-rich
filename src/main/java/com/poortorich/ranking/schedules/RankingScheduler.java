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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
        // 랭킹 기능을 활성화한 운영 중인 채팅방 목록 조회
        List<Chatroom> activeChatrooms = chatroomService.getChatroomsByRankingEnabledIsTrue();

        AtomicInteger counter = new AtomicInteger();
        // 조회한 채팅방 목록을 설정된 배치 크기 단위로 쪼개기
        Collection<List<Chatroom>> batches = activeChatrooms.stream()
                .collect(Collectors.groupingBy(chatroom -> counter.getAndIncrement() / batchSize))
                .values();

//        processBatch(activeChatrooms);

//         하나의 배치를 하나의 스레드에서 비동기로 처리
        batches.forEach(batch -> {
            CompletableFuture.runAsync(() -> {
                try {
                    processBatch(batch);
                } catch (Exception exception) {
                    log.error("랭킹 집계 실패");
                }
            }, taskExecutor);
        });
    }

    private void processBatch(List<Chatroom> chatrooms) {
        chatrooms.forEach(chatroom -> {
            // 랭킹 데이터 계산
            RankingResponsePayload payload = rankingFacade.calculateRanking(chatroom);

            if (!Objects.isNull(payload)) {
                // 채팅방에 랭킹 데이터를 담아 메세지 전송
                messagingTemplate.convertAndSend(
                        SubscribeEndpoint.CHATROOM_SUBSCRIBE_PREFIX + chatroom.getId(),
                        payload.mapToBasePayload()
                );
            }
        });
    }
}

package com.poortorich.ranking.schedules;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.service.ChatroomService;
import com.poortorich.ranking.facade.RankingFacade;
import com.poortorich.websocket.stomp.service.SubscribeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingSchedulerTest {

    @Mock
    private RankingFacade rankingFacade;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ChatroomService chatroomService;
    @Mock
    private SubscribeService subscribeService;
    @Mock
    private TaskExecutor taskExecutor;

    @Test
    void configuredBatchSize만큼_랭킹을_집계한다() {
        List<Chatroom> chatrooms = IntStream.range(0, 150)
                .mapToObj(index -> Chatroom.builder()
                        .id((long) index)
                        .title("chatroom-" + index)
                        .build())
                .toList();
        when(chatroomService.getChatroomsByRankingEnabledIsTrue()).thenReturn(chatrooms);
        when(rankingFacade.calculateRanking(org.mockito.ArgumentMatchers.any(Chatroom.class))).thenReturn(null);

        RankingScheduler scheduler = new RankingScheduler(
                rankingFacade,
                messagingTemplate,
                chatroomService,
                subscribeService,
                taskExecutor,
                100
        );

        scheduler.calculateAndBroadcastWeeklyRankingBatch();

        verify(rankingFacade, org.mockito.Mockito.times(100))
                .calculateRanking(argThat(chatrooms.subList(0, 100)::contains));
    }
}

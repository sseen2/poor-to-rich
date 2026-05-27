package com.poortorich.chat.schedules;

import com.poortorich.chat.service.ChatroomSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatroomSummaryScheduler {

    private final ChatroomSummaryService chatroomSummaryService;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void reconcileChatroomSummary() {
        try {
            chatroomSummaryService.reconcileSummaries();
        } catch (Exception exception) {
            log.error("채팅방 summary 정합성 보정 실패", exception);
        }
    }
}

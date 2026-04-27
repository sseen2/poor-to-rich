package com.poortorich.ranking.facade;

import com.poortorich.chat.service.ChatMessageService;
import com.poortorich.chat.service.ChatParticipantService;
import com.poortorich.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RankingTestFacade {

    private final RankingService rankingService;
    private final ChatMessageService chatMessageService;
    private final ChatParticipantService chatParticipantService;

    @Transactional
    public void deleteTestData(LocalDateTime since) {
        rankingService.deleteByCreatedDateAfter(since);
        chatMessageService.deleteRankingMessagesSince(since);
        chatParticipantService.resetAllRankingStatus();
    }
}

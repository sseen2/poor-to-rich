package com.poortorich.ranking.service;

import com.poortorich.accountbook.service.AccountBookService;
import com.poortorich.chat.entity.ChatParticipant;
import com.poortorich.chat.service.ChatParticipantService;
import com.poortorich.ranking.model.ChatroomUserExpenseAggregate;
import com.poortorich.ranking.model.RankingBatchSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingBatchReadService {

    private final ChatParticipantService chatParticipantService;
    private final AccountBookService accountBookService;

    @Transactional(readOnly = true)
    public RankingBatchSource read(List<Long> chatroomIds, LocalDate startDate, LocalDate endDate) {
        List<ChatParticipant> participants = chatParticipantService
                .findAllParticipatedByChatroomIdsWithUserAndChatroom(chatroomIds);
        Map<Long, List<ChatParticipant>> participantsByChatroomId = participants.stream()
                .collect(Collectors.groupingBy(participant -> participant.getChatroom().getId()));
        Map<Long, Map<Long, ChatParticipant>> participantByChatroomAndUserId = participants.stream()
                .collect(Collectors.groupingBy(
                        participant -> participant.getChatroom().getId(),
                        Collectors.toMap(
                                participant -> participant.getUser().getId(),
                                participant -> participant,
                                (left, right) -> left
                        )
                ));

        Map<Long, List<ChatroomUserExpenseAggregate>> aggregatesByChatroomId = accountBookService
                .getExpenseAggregatesForChatroomsInRange(chatroomIds, startDate, endDate)
                .stream()
                .collect(Collectors.groupingBy(ChatroomUserExpenseAggregate::getChatroomId));

        return new RankingBatchSource(
                participantsByChatroomId,
                participantByChatroomAndUserId,
                aggregatesByChatroomId
        );
    }
}

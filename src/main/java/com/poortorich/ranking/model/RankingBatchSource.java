package com.poortorich.ranking.model;

import com.poortorich.chat.entity.ChatParticipant;

import java.util.List;
import java.util.Map;

public record RankingBatchSource(
        Map<Long, List<ChatParticipant>> participantsByChatroomId,
        Map<Long, Map<Long, ChatParticipant>> participantByChatroomAndUserId,
        Map<Long, List<ChatroomUserExpenseAggregate>> aggregatesByChatroomId
) {
}

package com.poortorich.ranking.model;

import com.poortorich.chat.entity.ChatParticipant;
import com.poortorich.chat.entity.Chatroom;
import com.poortorich.ranking.entity.Ranking;

import java.util.List;

public record RankingCalculationResult(
        Chatroom chatroom,
        Ranking ranking,
        List<ChatParticipant> savers,
        List<ChatParticipant> flexers
) {
}

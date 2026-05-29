package com.poortorich.ranking.model;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.ranking.payload.response.RankingResponsePayload;
import lombok.Builder;

@Builder
public record BatchRankingResult(
        Chatroom chatroom,
        RankingResponsePayload payload
) {
}

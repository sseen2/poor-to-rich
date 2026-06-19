package com.poortorich.chat.realtime.event.user;

import com.poortorich.chat.entity.enums.RankingStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RankingProfileUpdateEvent {

    private final Long chatroomId;
    private final Long userId;
    private final String profileImage;
    private final String nickname;
    private final Boolean isHost;
    private final RankingStatus rankingStatus;
}

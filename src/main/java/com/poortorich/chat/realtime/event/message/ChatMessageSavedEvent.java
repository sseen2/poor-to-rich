package com.poortorich.chat.realtime.event.message;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

public record ChatMessageSavedEvent(
        Long chatroomId,
        Long messageId,
        String content,
        LocalDateTime sentAt,
        Instant publishedAt,
        List<Long> unreadUserIds
) {
}

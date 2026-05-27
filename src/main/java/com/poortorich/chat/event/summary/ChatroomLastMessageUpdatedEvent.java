package com.poortorich.chat.event.summary;

import com.poortorich.chat.entity.enums.ChatMessageType;
import java.time.LocalDateTime;

public record ChatroomLastMessageUpdatedEvent(
        Long chatroomId,
        ChatMessageType messageType,
        LocalDateTime sentAt
) {

}

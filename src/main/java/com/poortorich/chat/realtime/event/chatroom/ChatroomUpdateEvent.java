package com.poortorich.chat.realtime.event.chatroom;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.realtime.payload.response.enums.PayloadType;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class ChatroomUpdateEvent {

    private final Chatroom chatroom;
    private final Long chatroomId;
    private final PayloadType payloadType;
    private final Long messageId;
    private final String content;
    private final LocalDateTime sentAt;

    public ChatroomUpdateEvent(Chatroom chatroom, PayloadType payloadType) {
        this(chatroom, payloadType, null, null, null);
    }

    public ChatroomUpdateEvent(
            Chatroom chatroom,
            PayloadType payloadType,
            Long messageId,
            String content,
            LocalDateTime sentAt
    ) {
        this.chatroom = chatroom;
        this.chatroomId = chatroom.getId();
        this.payloadType = payloadType;
        this.messageId = messageId;
        this.content = content;
        this.sentAt = sentAt;
    }

    public boolean hasMessageUpdatePayload() {
        return PayloadType.CHATROOM_MESSAGE_UPDATED.equals(payloadType)
                && messageId != null
                && sentAt != null;
    }
}

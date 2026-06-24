package com.poortorich.chat.realtime.payload.request;

import com.poortorich.chat.constants.ChatResponseMessage;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarkMessagesAsReadRequestPayload {

    @NotNull(message = ChatResponseMessage.CHATROOM_ID_REQUIRED)
    private Long chatroomId;

    @NotNull(message = ChatResponseMessage.LAST_READ_MESSAGE_ID_REQUIRED)
    private Long lastReadMessageId;
}

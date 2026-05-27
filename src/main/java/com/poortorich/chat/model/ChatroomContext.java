package com.poortorich.chat.model;

import com.poortorich.chat.entity.Chatroom;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatroomContext {

    List<Chatroom> chatrooms;
    List<String> lastMessageTimes;
    List<Long> participantCounts;
    Boolean hasNext;
    String nextCursor;

    public boolean isEmpty() {
        return chatrooms == null || chatrooms.isEmpty();
    }
}

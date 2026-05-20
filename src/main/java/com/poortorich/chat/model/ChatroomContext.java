package com.poortorich.chat.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatroomContext {

    List<Long> chatroomIds;
    List<String> lastMessageTimes;
    Boolean hasNext;
    String nextCursor;

    public boolean isEmpty() {
        return chatroomIds == null || chatroomIds.isEmpty();
    }
}

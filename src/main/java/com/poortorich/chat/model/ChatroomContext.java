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
    Long version;
}

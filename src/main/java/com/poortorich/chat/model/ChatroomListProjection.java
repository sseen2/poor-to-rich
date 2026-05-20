package com.poortorich.chat.model;

public interface ChatroomListProjection {

    Long getChatroomId();

    String getLastMessageTime();

    String getCursorDateTime();

    Long getLikeCount();

    Long getParticipantCount();
}

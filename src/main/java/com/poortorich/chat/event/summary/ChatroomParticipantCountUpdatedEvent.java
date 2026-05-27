package com.poortorich.chat.event.summary;

public record ChatroomParticipantCountUpdatedEvent(
        Long chatroomId,
        Long participantCount
) {

}

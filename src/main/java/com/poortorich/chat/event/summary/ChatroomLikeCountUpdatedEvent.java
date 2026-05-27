package com.poortorich.chat.event.summary;

public record ChatroomLikeCountUpdatedEvent(
        Long chatroomId,
        Long likeCount
) {

}

package com.poortorich.chat.realtime.broadcast;

import com.poortorich.broadcast.BroadcastService;
import com.poortorich.chat.entity.ChatParticipant;
import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.realtime.payload.response.BasePayload;
import com.poortorich.chat.realtime.payload.response.enums.PayloadType;
import com.poortorich.chat.service.ChatMessageService;
import com.poortorich.chat.service.ChatParticipantService;
import com.poortorich.chat.service.UnreadChatMessageService;
import com.poortorich.chat.util.mapper.ChatroomMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatroomMessageUpdateBroadcaster {

    private final BroadcastService broadcastService;
    private final ChatMessageService chatMessageService;
    private final ChatParticipantService chatParticipantService;
    private final UnreadChatMessageService unreadChatMessageService;
    private final ChatroomMapper chatroomMapper;

    public void broadcast(Chatroom chatroom, Long messageId, String content, LocalDateTime sentAt) {
        if (!isLatestMessageUpdate(chatroom, messageId)) {
            log.debug("[SKIP_STALE_CHATROOM_MESSAGE_UPDATE] chatroomId={}, messageId={}", chatroom.getId(), messageId);
            return;
        }

        List<ChatParticipant> participants = chatParticipantService.findAllByChatroomWithUserAndChatroom(chatroom);
        Long currentMemberCount = chatParticipantService.countByChatroom(chatroom);
        Map<Long, Long> unreadMessageCountByUserId = unreadChatMessageService.countByUnreadChatMessages(chatroom);
        Map<Long, Long> latestReadMessageIdByParticipantId = chatMessageService.getLatestReadMessageIdsByParticipants(
                participants);

        participants.forEach(participant -> {
            BasePayload basePayload = BasePayload.builder()
                    .type(PayloadType.CHATROOM_MESSAGE_UPDATED)
                    .payload(chatroomMapper.mapToMyChatroom(
                            participant,
                            content,
                            sentAt,
                            currentMemberCount,
                            latestReadMessageIdByParticipantId.get(participant.getId()),
                            unreadMessageCountByUserId.getOrDefault(participant.getUser().getId(), 0L)))
                    .build();

            broadcastService.broadcastInMyChatroom(participant.getUser().getId(), basePayload);
        });
    }

    private boolean isLatestMessageUpdate(Chatroom chatroom, Long messageId) {
        Long latestMessageId = chatMessageService.getLatestMessageIdWithTypes(chatroom);
        return latestMessageId == null || messageId >= latestMessageId;
    }
}

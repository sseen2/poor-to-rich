package com.poortorich.chat.realtime.event.chatroom;

import com.poortorich.broadcast.BroadcastService;
import com.poortorich.chat.entity.ChatParticipant;
import com.poortorich.chat.realtime.payload.response.BasePayload;
import com.poortorich.chat.realtime.payload.response.enums.PayloadType;
import com.poortorich.chat.service.ChatMessageService;
import com.poortorich.chat.service.ChatParticipantService;
import com.poortorich.chat.service.UnreadChatMessageService;
import com.poortorich.chat.util.mapper.ChatroomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatroomUpdateEventListener {

    private final BroadcastService broadcastService;
    private final ChatMessageService chatMessageService;
    private final ChatParticipantService chatParticipantService;
    private final UnreadChatMessageService unreadChatMessageService;
    private final ChatroomMapper chatroomMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatroomUpdated(ChatroomUpdateEvent event) {
        if (event.hasMessageUpdatePayload()) {
            broadcastChatroomMessageUpdated(event);
            return;
        }

        List<ChatParticipant> participants = chatParticipantService.findAllByChatroom(event.getChatroom());

        participants.forEach(participant -> {
            BasePayload basePayload = BasePayload.builder()
                    .type(event.getPayloadType())
                    .payload(chatroomMapper.mapToMyChatroom(participant))
                    .build();

            broadcastService.broadcastInMyChatroom(participant.getUser().getId(), basePayload);
        });
    }

    private void broadcastChatroomMessageUpdated(ChatroomUpdateEvent event) {
        List<ChatParticipant> participants = chatParticipantService.findAllByChatroomWithUserAndChatroom(
                event.getChatroom());
        Long currentMemberCount = chatParticipantService.countByChatroom(event.getChatroom());
        Map<Long, Long> unreadMessageCountByUserId = unreadChatMessageService.countByUnreadChatMessages(
                event.getChatroom());
        Map<Long, Long> latestReadMessageIdByParticipantId = chatMessageService.getLatestReadMessageIdsByParticipants(
                participants);

        participants.forEach(participant -> {
            BasePayload basePayload = BasePayload.builder()
                    .type(event.getPayloadType())
                    .payload(chatroomMapper.mapToMyChatroom(
                            participant,
                            event.getContent(),
                            event.getSentAt(),
                            currentMemberCount,
                            latestReadMessageIdByParticipantId.get(participant.getId()),
                            unreadMessageCountByUserId.getOrDefault(participant.getUser().getId(), 0L)))
                    .build();

            broadcastService.broadcastInMyChatroom(participant.getUser().getId(), basePayload);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatParticipantUpdated(ParticipantUpdateEvent event) {
        ChatParticipant participant = event.getParticipant();

        BasePayload basePayload = BasePayload.builder()
                .type(PayloadType.CHATROOM_INFO_UPDATED)
                .payload(chatroomMapper.mapToMyChatroom(participant))
                .build();

        broadcastService.broadcastInMyChatroom(participant.getUser().getId(), basePayload);
    }
}

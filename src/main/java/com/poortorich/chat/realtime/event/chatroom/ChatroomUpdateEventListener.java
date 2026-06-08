package com.poortorich.chat.realtime.event.chatroom;

import com.poortorich.broadcast.BroadcastService;
import com.poortorich.chat.entity.ChatParticipant;
import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.realtime.payload.response.BasePayload;
import com.poortorich.chat.realtime.payload.response.enums.PayloadType;
import com.poortorich.chat.service.ChatMessageService;
import com.poortorich.chat.service.ChatParticipantService;
import com.poortorich.chat.service.ChatroomService;
import com.poortorich.chat.service.UnreadChatMessageService;
import com.poortorich.chat.util.mapper.ChatroomMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@Slf4j
public class ChatroomUpdateEventListener {

    private final BroadcastService broadcastService;
    private final ChatMessageService chatMessageService;
    private final ChatParticipantService chatParticipantService;
    private final ChatroomService chatroomService;
    private final UnreadChatMessageService unreadChatMessageService;
    private final ChatroomMapper chatroomMapper;
    private final TaskExecutor taskExecutor;
    private final Timer processDelayTimer;
    private final Timer processDurationTimer;
    private final Timer chatroomQueryTimer;
    private final Timer participantsQueryTimer;
    private final Timer memberCountQueryTimer;
    private final Timer unreadCountQueryTimer;
    private final Timer latestReadMessageQueryTimer;
    private final Timer summaryMappingTimer;

    public ChatroomUpdateEventListener(
            BroadcastService broadcastService,
            ChatMessageService chatMessageService,
            ChatParticipantService chatParticipantService,
            ChatroomService chatroomService,
            UnreadChatMessageService unreadChatMessageService,
            ChatroomMapper chatroomMapper,
            @Qualifier("chatMessagePostTaskExecutor") TaskExecutor taskExecutor,
            MeterRegistry meterRegistry
    ) {
        this.broadcastService = broadcastService;
        this.chatMessageService = chatMessageService;
        this.chatParticipantService = chatParticipantService;
        this.chatroomService = chatroomService;
        this.unreadChatMessageService = unreadChatMessageService;
        this.chatroomMapper = chatroomMapper;
        this.taskExecutor = taskExecutor;
        this.processDelayTimer = Timer.builder("chat.message.post.process.delay")
                .description("Delay between chat message creation and post-processing start")
                .register(meterRegistry);
        this.processDurationTimer = Timer.builder("chat.message.post.process.duration")
                .description("Time spent processing chat message post-processing task")
                .register(meterRegistry);
        this.chatroomQueryTimer = Timer.builder("chat.message.post.chatroom.query.duration")
                .description("Time spent loading chatroom during chat message post-processing")
                .register(meterRegistry);
        this.participantsQueryTimer = Timer.builder("chat.message.post.participants.query.duration")
                .description("Time spent loading chatroom participants during chat message post-processing")
                .register(meterRegistry);
        this.memberCountQueryTimer = Timer.builder("chat.message.post.member.count.query.duration")
                .description("Time spent counting chatroom members during chat message post-processing")
                .register(meterRegistry);
        this.unreadCountQueryTimer = Timer.builder("chat.message.post.unread.count.query.duration")
                .description("Time spent counting unread chat messages during chat message post-processing")
                .register(meterRegistry);
        this.latestReadMessageQueryTimer = Timer.builder("chat.message.post.latest.read.query.duration")
                .description("Time spent loading latest read message ids during chat message post-processing")
                .register(meterRegistry);
        this.summaryMappingTimer = Timer.builder("chat.message.post.summary.mapping.duration")
                .description("Time spent mapping my chatroom summary payloads during chat message post-processing")
                .register(meterRegistry);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatroomUpdated(ChatroomUpdateEvent event) {
        executeChatMessagePostProcess(() -> broadcastChatroomUpdated(event));
    }

    private void broadcastChatroomUpdated(ChatroomUpdateEvent event) {
        Chatroom chatroom = record(chatroomQueryTimer, () -> chatroomService.findById(event.getChatroomId()));

        if (event.hasMessageUpdatePayload()) {
            recordProcessDelay(event.getSentAt());

            if (!isLatestMessageEvent(chatroom, event.getMessageId())) {
                return;
            }

            broadcastChatroomMessageUpdated(chatroom, event);
            return;
        }

        broadcastChatroomInfoUpdated(chatroom, event);
    }

    private void broadcastChatroomInfoUpdated(Chatroom chatroom, ChatroomUpdateEvent event) {
        List<ChatParticipant> participants = record(participantsQueryTimer,
                () -> chatParticipantService.findAllByChatroomWithUserAndChatroom(chatroom));

        participants.forEach(participant -> {
            BasePayload basePayload = BasePayload.builder()
                    .type(event.getPayloadType())
                    .payload(record(summaryMappingTimer, () -> chatroomMapper.mapToMyChatroom(participant)))
                    .build();

            broadcastService.broadcastInMyChatroom(participant.getUser().getId(), basePayload);
        });
    }

    private void broadcastChatroomMessageUpdated(Chatroom chatroom, ChatroomUpdateEvent event) {
        List<ChatParticipant> participants = record(participantsQueryTimer,
                () -> chatParticipantService.findAllByChatroomWithUserAndChatroom(chatroom));
        Long currentMemberCount = record(memberCountQueryTimer,
                () -> chatParticipantService.countByChatroom(chatroom));
        Map<Long, Long> unreadMessageCountByUserId = record(unreadCountQueryTimer,
                () -> unreadChatMessageService.countByUnreadChatMessages(chatroom));
        Map<Long, Long> latestReadMessageIdByParticipantId = record(latestReadMessageQueryTimer,
                () -> chatMessageService.getLatestReadMessageIdsByParticipants(participants));

        participants.forEach(participant -> {
            BasePayload basePayload = BasePayload.builder()
                    .type(event.getPayloadType())
                    .payload(record(summaryMappingTimer, () -> chatroomMapper.mapToMyChatroom(
                            participant,
                            event.getContent(),
                            event.getSentAt(),
                            currentMemberCount,
                            latestReadMessageIdByParticipantId.get(participant.getId()),
                            unreadMessageCountByUserId.getOrDefault(participant.getUser().getId(), 0L))))
                    .build();

            broadcastService.broadcastInMyChatroom(participant.getUser().getId(), basePayload);
        });
    }

    private boolean isLatestMessageEvent(Chatroom chatroom, Long eventMessageId) {
        Long latestMessageId = chatMessageService.getLatestMessageId(chatroom);
        return latestMessageId == null || latestMessageId <= eventMessageId;
    }

    private void recordProcessDelay(LocalDateTime sentAt) {
        if (sentAt == null) {
            return;
        }

        Duration delay = Duration.between(sentAt, LocalDateTime.now());
        if (delay.isNegative()) {
            delay = Duration.ZERO;
        }
        processDelayTimer.record(delay);
    }

    private void executeChatMessagePostProcess(Runnable task) {
        try {
            taskExecutor.execute(() -> {
                try {
                    processDurationTimer.record(task);
                } catch (RuntimeException exception) {
                    log.warn("[CHAT_MESSAGE_POST_PROCESS_FAILED] async chatroom update task failed", exception);
                }
            });
        } catch (TaskRejectedException exception) {
            log.warn("[CHAT_MESSAGE_POST_PROCESS_REJECTED] async chatroom update task rejected", exception);
        }
    }

    private <T> T record(Timer timer, Supplier<T> supplier) {
        long startTime = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            timer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
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

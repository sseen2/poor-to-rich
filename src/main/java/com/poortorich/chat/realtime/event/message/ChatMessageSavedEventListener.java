package com.poortorich.chat.realtime.event.message;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.realtime.broadcast.ChatroomMessageUpdateBroadcaster;
import com.poortorich.chat.service.ChatroomService;
import com.poortorich.chat.service.UnreadChatMessageService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class ChatMessageSavedEventListener {

    private final UnreadChatMessageService unreadChatMessageService;
    private final ChatroomService chatroomService;
    private final ChatroomMessageUpdateBroadcaster chatroomMessageUpdateBroadcaster;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final Timer postProcessDelayTimer;

    public ChatMessageSavedEventListener(
            UnreadChatMessageService unreadChatMessageService,
            ChatroomService chatroomService,
            ChatroomMessageUpdateBroadcaster chatroomMessageUpdateBroadcaster,
            @Qualifier("chatMessagePostTaskExecutor") ThreadPoolTaskExecutor taskExecutor,
            MeterRegistry meterRegistry
    ) {
        this.unreadChatMessageService = unreadChatMessageService;
        this.chatroomService = chatroomService;
        this.chatroomMessageUpdateBroadcaster = chatroomMessageUpdateBroadcaster;
        this.taskExecutor = taskExecutor;
        this.postProcessDelayTimer = Timer.builder("chat.message.post.process.delay")
                .description("Delay between chat message event publication and post-processing start")
                .register(meterRegistry);

        Gauge.builder("chat.message.post.executor.queue.size", taskExecutor,
                        executor -> executor.getThreadPoolExecutor().getQueue().size())
                .description("Queued chat message post-processing tasks")
                .register(meterRegistry);
        Gauge.builder("chat.message.post.executor.active.count", taskExecutor,
                        ThreadPoolTaskExecutor::getActiveCount)
                .description("Active chat message post-processing threads")
                .register(meterRegistry);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessageSaved(ChatMessageSavedEvent event) {
        taskExecutor.execute(() -> processAfterMessageSaved(event));
    }

    private void processAfterMessageSaved(ChatMessageSavedEvent event) {
        try {
            recordPostProcessDelay(event.publishedAt());

            unreadChatMessageService.saveUnreadMembers(
                    event.chatroomId(),
                    event.messageId(),
                    event.unreadUserIds());

            Chatroom chatroom = chatroomService.findById(event.chatroomId());
            chatroomMessageUpdateBroadcaster.broadcast(
                    chatroom,
                    event.messageId(),
                    event.content(),
                    event.sentAt());
        } catch (RuntimeException e) {
            log.error("[CHAT_MESSAGE_POST_PROCESS_FAILED] chatroomId={}, messageId={}",
                    event.chatroomId(),
                    event.messageId(),
                    e);
        }
    }

    private void recordPostProcessDelay(Instant publishedAt) {
        postProcessDelayTimer.record(Duration.between(publishedAt, Instant.now()));
    }
}

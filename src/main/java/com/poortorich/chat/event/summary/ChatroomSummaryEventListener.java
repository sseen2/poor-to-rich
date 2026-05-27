package com.poortorich.chat.event.summary;

import com.poortorich.chat.service.ChatroomSummaryService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatroomSummaryEventListener {

    private final ChatroomSummaryService chatroomSummaryService;
    private final TaskExecutor taskExecutor;

    public ChatroomSummaryEventListener(
            ChatroomSummaryService chatroomSummaryService,
            @Qualifier("chatroomSummaryTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.chatroomSummaryService = chatroomSummaryService;
        this.taskExecutor = taskExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLastMessageUpdated(ChatroomLastMessageUpdatedEvent event) {
        executeSummaryUpdate(() -> chatroomSummaryService.updateLastMessage(
                event.chatroomId(),
                event.messageType(),
                event.sentAt()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLikeCountUpdated(ChatroomLikeCountUpdatedEvent event) {
        executeSummaryUpdate(() -> chatroomSummaryService.updateLikeCount(
                event.chatroomId(),
                event.likeCount()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onParticipantCountUpdated(ChatroomParticipantCountUpdatedEvent event) {
        executeSummaryUpdate(() -> chatroomSummaryService.updateParticipantCount(
                event.chatroomId(),
                event.participantCount()
        ));
    }

    private void executeSummaryUpdate(Runnable task) {
        taskExecutor.execute(task);
    }
}

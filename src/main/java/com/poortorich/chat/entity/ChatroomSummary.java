package com.poortorich.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Getter
@Builder
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "chatroom_summary",
        indexes = {
                @Index(name = "idx_chatroom_summary_created", columnList = "is_closed, chatroom_id"),
                @Index(name = "idx_chatroom_summary_updated", columnList = "is_closed, last_message_at, chatroom_id"),
                @Index(name = "idx_chatroom_summary_like", columnList = "is_closed, like_count, participant_count, chatroom_id")
        }
)
public class ChatroomSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatroom_id", nullable = false, unique = true)
    private Chatroom chatroom;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private Long likeCount = 0L;

    @Column(name = "participant_count", nullable = false)
    @Builder.Default
    private Long participantCount = 0L;

    @Column(name = "is_closed", nullable = false)
    @Builder.Default
    private Boolean isClosed = Boolean.FALSE;

    public static ChatroomSummary create(Chatroom chatroom, Long participantCount) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = chatroom.getCreatedDate() == null ? now : chatroom.getCreatedDate();

        return ChatroomSummary.builder()
                .chatroom(chatroom)
                .createdAt(createdAt)
                .lastMessageAt(createdAt)
                .likeCount(0L)
                .participantCount(participantCount)
                .isClosed(chatroom.getIsClosed())
                .build();
    }

    public static ChatroomSummary createForReconcile(
            Chatroom chatroom,
            LocalDateTime createdAt,
            LocalDateTime lastMessageAt,
            Long likeCount,
            Long participantCount,
            Boolean isClosed
    ) {
        LocalDateTime resolvedCreatedAt = createdAt == null ? LocalDateTime.now() : createdAt;

        return ChatroomSummary.builder()
                .chatroom(chatroom)
                .createdAt(resolvedCreatedAt)
                .lastMessageAt(lastMessageAt == null ? resolvedCreatedAt : lastMessageAt)
                .likeCount(likeCount)
                .participantCount(participantCount)
                .isClosed(isClosed)
                .build();
    }

    public void updateLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public void updateCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void updateLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    public void updateParticipantCount(Long participantCount) {
        this.participantCount = participantCount;
    }

    public void updateClosedStatus(Boolean isClosed) {
        this.isClosed = isClosed;
    }

    public void close() {
        this.isClosed = Boolean.TRUE;
    }
}

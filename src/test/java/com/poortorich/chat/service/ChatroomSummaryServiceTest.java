package com.poortorich.chat.service;

import com.poortorich.chat.entity.ChatMessage;
import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.entity.ChatroomSummary;
import com.poortorich.chat.entity.enums.ChatMessageType;
import com.poortorich.chat.model.ChatroomContext;
import com.poortorich.chat.repository.ChatroomSummaryRepository;
import com.poortorich.chat.request.enums.SortBy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatroomSummaryServiceTest {

    @Mock
    private ChatroomSummaryRepository chatroomSummaryRepository;

    @InjectMocks
    private ChatroomSummaryService chatroomSummaryService;

    @Test
    @DisplayName("최근생성순 summary 목록 조회 시 다음 커서를 생성한다")
    void getChatroomContextSortByCreatedAtSuccess() {
        ChatroomSummary first = createSummary(3L, LocalDateTime.of(2026, 1, 3, 0, 0), 0L, 1L);
        ChatroomSummary second = createSummary(2L, LocalDateTime.of(2026, 1, 2, 0, 0), 0L, 1L);
        ChatroomSummary extra = createSummary(1L, LocalDateTime.of(2026, 1, 1, 0, 0), 0L, 1L);

        when(chatroomSummaryRepository.findByCreatedAtCursor(null, PageRequest.of(0, 3)))
                .thenReturn(List.of(first, second, extra));

        ChatroomContext result = chatroomSummaryService.getChatroomContext(SortBy.CREATED_AT, "-1", 2);

        assertThat(result.getChatroomIds()).containsExactly(3L, 2L);
        assertThat(result.getHasNext()).isTrue();
        assertThat(result.getNextCursor()).isEqualTo("2");
    }

    @Test
    @DisplayName("최근대화순 summary 목록 조회 시 복합 커서를 사용한다")
    void getChatroomContextSortByUpdatedAtSuccess() {
        LocalDateTime cursorDateTime = LocalDateTime.of(2026, 1, 2, 0, 0);
        ChatroomSummary summary = createSummary(1L, LocalDateTime.of(2026, 1, 1, 0, 0), 0L, 1L);

        when(chatroomSummaryRepository.findByUpdatedAtCursor(cursorDateTime, 2L, PageRequest.of(0, 3)))
                .thenReturn(List.of(summary));

        ChatroomContext result = chatroomSummaryService.getChatroomContext(
                SortBy.UPDATED_AT,
                "2026-01-02T00:00,2",
                2
        );

        assertThat(result.getChatroomIds()).containsExactly(1L);
        assertThat(result.getHasNext()).isFalse();
        assertThat(result.getNextCursor()).isEqualTo("2026-01-01T00:00,1");
    }

    @Test
    @DisplayName("좋아요순 summary 목록 조회 시 좋아요 수, 참여자 수, id 복합 커서를 사용한다")
    void getChatroomContextSortByLikeSuccess() {
        ChatroomSummary summary = createSummary(1L, LocalDateTime.of(2026, 1, 1, 0, 0), 7L, 3L);

        when(chatroomSummaryRepository.findByLikeCursor(10L, 5L, 2L, PageRequest.of(0, 3)))
                .thenReturn(List.of(summary));

        ChatroomContext result = chatroomSummaryService.getChatroomContext(SortBy.LIKE, "10,5,2", 2);

        assertThat(result.getChatroomIds()).containsExactly(1L);
        assertThat(result.getHasNext()).isFalse();
        assertThat(result.getNextCursor()).isEqualTo("7,3,1");
    }

    @Test
    @DisplayName("채팅 메시지 저장 시 summary의 최근 메시지 시간을 갱신한다")
    void updateLastMessageSuccess() {
        Chatroom chatroom = Chatroom.builder().id(1L).build();
        LocalDateTime sentAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        ChatroomSummary summary = createSummary(1L, LocalDateTime.of(2026, 1, 1, 0, 0), 0L, 1L);
        ChatMessage message = ChatMessage.builder()
                .chatroom(chatroom)
                .type(ChatMessageType.CHAT_MESSAGE)
                .sentAt(sentAt)
                .build();

        when(chatroomSummaryRepository.findByChatroom(chatroom)).thenReturn(Optional.of(summary));

        chatroomSummaryService.updateLastMessage(message);

        assertThat(summary.getLastMessageAt()).isEqualTo(sentAt);
    }

    @Test
    @DisplayName("목록 정렬 대상이 아닌 메시지는 summary를 갱신하지 않는다")
    void updateLastMessageIgnoreNotOrderingMessage() {
        ChatMessage message = ChatMessage.builder()
                .type(ChatMessageType.SYSTEM_MESSAGE)
                .build();

        chatroomSummaryService.updateLastMessage(message);

        verifyNoInteractions(chatroomSummaryRepository);
    }

    @Test
    @DisplayName("채팅방 생성 시 summary를 저장한다")
    void createSummarySuccess() {
        Chatroom chatroom = Chatroom.builder()
                .id(1L)
                .createdDate(LocalDateTime.of(2026, 1, 1, 0, 0))
                .isClosed(false)
                .build();

        chatroomSummaryService.createSummary(chatroom, 1L);

        verify(chatroomSummaryRepository).save(org.mockito.ArgumentMatchers.any(ChatroomSummary.class));
    }

    private ChatroomSummary createSummary(
            Long chatroomId,
            LocalDateTime lastMessageAt,
            Long likeCount,
            Long participantCount
    ) {
        Chatroom chatroom = Chatroom.builder()
                .id(chatroomId)
                .createdDate(lastMessageAt)
                .isClosed(false)
                .build();

        return ChatroomSummary.builder()
                .chatroom(chatroom)
                .createdAt(lastMessageAt)
                .lastMessageAt(lastMessageAt)
                .likeCount(likeCount)
                .participantCount(participantCount)
                .isClosed(false)
                .build();
    }
}

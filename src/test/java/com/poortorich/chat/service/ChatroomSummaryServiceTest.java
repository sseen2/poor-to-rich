package com.poortorich.chat.service;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.entity.ChatroomSummary;
import com.poortorich.chat.entity.enums.ChatMessageType;
import com.poortorich.chat.model.ChatroomContext;
import com.poortorich.chat.repository.ChatMessageRepository;
import com.poortorich.chat.repository.ChatParticipantRepository;
import com.poortorich.chat.repository.ChatroomRepository;
import com.poortorich.chat.repository.ChatroomSummaryRepository;
import com.poortorich.chat.request.enums.SortBy;
import com.poortorich.like.repository.LikeRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatroomSummaryServiceTest {

    @Mock
    private ChatroomSummaryRepository chatroomSummaryRepository;
    @Mock
    private ChatroomRepository chatroomRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatParticipantRepository chatParticipantRepository;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private TransactionTemplate transactionTemplate;

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

        assertThat(result.getChatrooms())
                .extracting(Chatroom::getId)
                .containsExactly(3L, 2L);
        assertThat(result.getParticipantCounts()).containsExactly(1L, 1L);
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

        assertThat(result.getChatrooms())
                .extracting(Chatroom::getId)
                .containsExactly(1L);
        assertThat(result.getParticipantCounts()).containsExactly(1L);
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

        assertThat(result.getChatrooms())
                .extracting(Chatroom::getId)
                .containsExactly(1L);
        assertThat(result.getParticipantCounts()).containsExactly(3L);
        assertThat(result.getHasNext()).isFalse();
        assertThat(result.getNextCursor()).isEqualTo("7,3,1");
    }

    @Test
    @DisplayName("채팅 메시지 저장 시 summary의 최근 메시지 시간을 갱신한다")
    void updateLastMessageSuccess() {
        Chatroom chatroom = Chatroom.builder().id(1L).build();
        LocalDateTime sentAt = LocalDateTime.of(2026, 1, 1, 12, 0);

        chatroomSummaryService.updateLastMessage(chatroom.getId(), ChatMessageType.CHAT_MESSAGE, sentAt);

        verify(chatroomSummaryRepository).updateLastMessageAt(chatroom.getId(), sentAt);
    }

    @Test
    @DisplayName("목록 정렬 대상이 아닌 메시지는 summary를 갱신하지 않는다")
    void updateLastMessageIgnoreNotOrderingMessage() {
        chatroomSummaryService.updateLastMessage(1L, ChatMessageType.SYSTEM_MESSAGE, LocalDateTime.now());

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

    @Test
    @DisplayName("summary 정합성 보정 시 누락 생성 후 원본 기준으로 갱신한다")
    void reconcileSummariesSuccess() {
        PageRequest pageRequest = PageRequest.of(0, 1000);
        LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime secondCreatedAt = LocalDateTime.of(2026, 1, 2, 0, 0);
        LocalDateTime firstLastMessageAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime secondLastMessageAt = LocalDateTime.of(2026, 1, 2, 12, 0);
        Chatroom firstChatroom = Chatroom.builder()
                .id(1L)
                .createdDate(firstCreatedAt)
                .isClosed(false)
                .build();
        Chatroom secondChatroom = Chatroom.builder()
                .id(2L)
                .createdDate(secondCreatedAt)
                .isClosed(false)
                .build();
        ChatroomSummary secondSummary = ChatroomSummary.builder()
                .chatroom(secondChatroom)
                .createdAt(secondCreatedAt)
                .lastMessageAt(secondCreatedAt)
                .likeCount(0L)
                .participantCount(1L)
                .isClosed(false)
                .build();

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(chatroomRepository.findByIdGreaterThanOrderByIdAsc(0L, pageRequest))
                .thenReturn(List.of(firstChatroom, secondChatroom));
        when(chatroomRepository.findByIdGreaterThanOrderByIdAsc(2L, pageRequest))
                .thenReturn(List.of());
        when(chatroomSummaryRepository.findByChatroom_IdIn(List.of(1L, 2L)))
                .thenReturn(List.of(secondSummary));
        when(chatMessageRepository.findLastMessageTimesByChatroomIds(
                List.of(1L, 2L),
                List.of(ChatMessageType.CHAT_MESSAGE, ChatMessageType.RANKING_MESSAGE)
        )).thenReturn(List.of(
                new Object[]{1L, firstLastMessageAt},
                new Object[]{2L, secondLastMessageAt}
        ));
        when(likeRepository.countByChatroomIds(List.of(1L, 2L)))
                .thenReturn(List.of(
                        new Object[]{1L, 3L},
                        new Object[]{2L, 5L}
                ));
        when(chatParticipantRepository.countParticipantsByChatroomIds(List.of(1L, 2L)))
                .thenReturn(List.of(
                        new Object[]{1L, 2L},
                        new Object[]{2L, 4L}
                ));

        chatroomSummaryService.reconcileSummaries();

        verify(chatroomSummaryRepository).saveAll(org.mockito.ArgumentMatchers.argThat(summaries -> {
            List<ChatroomSummary> savedSummaries = (List<ChatroomSummary>) summaries;
            return savedSummaries.size() == 1
                    && savedSummaries.getFirst().getChatroom().getId().equals(1L)
                    && savedSummaries.getFirst().getLastMessageAt().equals(firstLastMessageAt)
                    && savedSummaries.getFirst().getLikeCount().equals(3L)
                    && savedSummaries.getFirst().getParticipantCount().equals(2L);
        }));
        assertThat(secondSummary.getLastMessageAt()).isEqualTo(secondLastMessageAt);
        assertThat(secondSummary.getLikeCount()).isEqualTo(5L);
        assertThat(secondSummary.getParticipantCount()).isEqualTo(4L);
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

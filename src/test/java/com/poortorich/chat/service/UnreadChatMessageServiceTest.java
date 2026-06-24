package com.poortorich.chat.service;

import com.poortorich.chat.entity.ChatMessage;
import com.poortorich.chat.entity.ChatParticipant;
import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.entity.enums.ChatroomRole;
import com.poortorich.chat.realtime.payload.response.MessageReadPayload;
import com.poortorich.chat.repository.ChatMessageRepository;
import com.poortorich.chat.repository.ChatParticipantRepository;
import com.poortorich.chat.repository.UnreadChatMessageBulkRepository;
import com.poortorich.chat.repository.UnreadChatMessageRepository;
import com.poortorich.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnreadChatMessageServiceTest {

    @Mock
    private UnreadChatMessageRepository unreadChatMessageRepository;

    @Mock
    private UnreadChatMessageBulkRepository unreadChatMessageBulkRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatParticipantRepository chatParticipantRepository;

    @InjectMocks
    private UnreadChatMessageService unreadChatMessageService;

    @Test
    @DisplayName("안 읽은 메시지 대상자를 bulk insert로 저장하고 사용자 ID 목록을 반환한다")
    void saveUnreadMemberSuccess() {
        ChatMessage chatMessage = ChatMessage.builder().id(10L).build();
        Chatroom chatroom = Chatroom.builder().id(1L).build();
        User firstUser = User.builder().id(2L).build();
        User secondUser = User.builder().id(3L).build();
        List<ChatParticipant> chatMembers = List.of(
                ChatParticipant.builder().user(firstUser).chatroom(chatroom).build(),
                ChatParticipant.builder().user(secondUser).chatroom(chatroom).build()
        );

        List<Long> result = unreadChatMessageService.saveUnreadMember(chatMessage, chatMembers);

        assertThat(result).containsExactly(2L, 3L);
        verify(unreadChatMessageBulkRepository).saveAll(chatMessage, chatMembers);
        verify(unreadChatMessageRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("안 읽은 메시지 대상자가 없으면 저장소를 호출하지 않는다")
    void saveUnreadMemberEmpty() {
        List<Long> result = unreadChatMessageService.saveUnreadMember(
                ChatMessage.builder().id(10L).build(),
                List.of());

        assertThat(result).isEmpty();
        verify(unreadChatMessageBulkRepository, never()).saveAll(any(), anyList());
        verify(unreadChatMessageRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("여러 채팅 메시지의 안 읽은 사용자 목록을 메시지별로 묶어 조회한다")
    void getUserIdsByChatMessagesSuccess() {
        Long currentUserId = 1L;
        ChatMessage firstMessage = ChatMessage.builder().id(10L).build();
        ChatMessage secondMessage = ChatMessage.builder().id(11L).build();

        when(unreadChatMessageRepository.findUserIdsByChatMessageIdsExcludingChatroomRole(
                currentUserId,
                List.of(10L, 11L),
                ChatroomRole.BANNED
        )).thenReturn(List.of(
                new Object[]{10L, 2L},
                new Object[]{10L, 3L},
                new Object[]{11L, 4L}
        ));

        Map<Long, List<Long>> result = unreadChatMessageService.getUserIdsByChatMessages(
                currentUserId,
                List.of(firstMessage, secondMessage));

        assertThat(result.get(10L)).containsExactly(2L, 3L);
        assertThat(result.get(11L)).containsExactly(4L);
    }

    @Test
    @DisplayName("조회할 채팅 메시지가 없으면 저장소를 호출하지 않는다")
    void getUserIdsByChatMessagesEmpty() {
        Map<Long, List<Long>> result = unreadChatMessageService.getUserIdsByChatMessages(1L, List.of());

        assertThat(result).isEmpty();
        verify(unreadChatMessageRepository, never())
                .findUserIdsByChatMessageIdsExcludingChatroomRole(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("마지막으로 확인한 메시지 ID까지 읽음 처리하고 증가된 커서를 반환한다")
    void markMessageAsReadUntilLastReadMessageId() {
        Long lastReadMessageId = 10L;
        User user = User.builder().id(2L).build();
        Chatroom chatroom = Chatroom.builder().id(1L).build();
        ChatParticipant participant = ChatParticipant.builder()
                .id(3L)
                .user(user)
                .chatroom(chatroom)
                .build();

        when(chatMessageRepository.existsByIdAndChatroom(lastReadMessageId, chatroom)).thenReturn(true);
        when(chatParticipantRepository.findLatestReadMessageId(participant)).thenReturn(lastReadMessageId);

        MessageReadPayload result = unreadChatMessageService.markMessageAsRead(participant, lastReadMessageId);

        assertThat(result.getLastReadMessageId()).isEqualTo(lastReadMessageId);
        verify(chatParticipantRepository).advanceLatestReadMessageId(participant, lastReadMessageId);
        verify(unreadChatMessageRepository).markMessagesAsRead(chatroom, user, lastReadMessageId);
    }

    @Test
    @DisplayName("늦게 도착한 과거 읽음 요청은 읽음 커서를 후퇴시키지 않는다")
    void markMessageAsReadDoesNotMoveCursorBackward() {
        Long delayedLastReadMessageId = 10L;
        Long currentLastReadMessageId = 12L;
        User user = User.builder().id(2L).build();
        Chatroom chatroom = Chatroom.builder().id(1L).build();
        ChatParticipant participant = ChatParticipant.builder()
                .id(3L)
                .user(user)
                .chatroom(chatroom)
                .build();

        when(chatMessageRepository.existsByIdAndChatroom(delayedLastReadMessageId, chatroom)).thenReturn(true);
        when(chatParticipantRepository.findLatestReadMessageId(participant)).thenReturn(currentLastReadMessageId);

        MessageReadPayload result = unreadChatMessageService.markMessageAsRead(participant, delayedLastReadMessageId);

        assertThat(result.getLastReadMessageId()).isEqualTo(currentLastReadMessageId);
        verify(chatParticipantRepository).advanceLatestReadMessageId(participant, delayedLastReadMessageId);
        verify(unreadChatMessageRepository).markMessagesAsRead(chatroom, user, delayedLastReadMessageId);
    }

    @Test
    @DisplayName("다른 채팅방 메시지 ID로 읽음 처리할 수 없다")
    void markMessageAsReadRejectsMessageFromAnotherChatroom() {
        Long lastReadMessageId = 10L;
        Chatroom chatroom = Chatroom.builder().id(1L).build();
        ChatParticipant participant = ChatParticipant.builder()
                .id(3L)
                .user(User.builder().id(2L).build())
                .chatroom(chatroom)
                .build();

        when(chatMessageRepository.existsByIdAndChatroom(lastReadMessageId, chatroom)).thenReturn(false);

        assertThatThrownBy(() -> unreadChatMessageService.markMessageAsRead(participant, lastReadMessageId))
                .hasMessage("채팅 메시지를 찾을 수 없습니다.");

        verify(chatParticipantRepository, never()).advanceLatestReadMessageId(participant, lastReadMessageId);
    }
}

package com.poortorich.chat.service;

import com.poortorich.chat.entity.ChatMessage;
import com.poortorich.chat.entity.enums.ChatroomRole;
import com.poortorich.chat.repository.UnreadChatMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnreadChatMessageServiceTest {

    @Mock
    private UnreadChatMessageRepository unreadChatMessageRepository;

    @InjectMocks
    private UnreadChatMessageService unreadChatMessageService;

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
}

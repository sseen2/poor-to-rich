package com.poortorich.chat.repository;

import com.poortorich.chat.entity.ChatMessage;
import com.poortorich.chat.entity.ChatParticipant;
import com.poortorich.chat.entity.Chatroom;
import com.poortorich.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UnreadChatMessageBulkRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private UnreadChatMessageBulkRepository bulkRepository;

    @Test
    @DisplayName("여러 미읽음 대상을 하나의 multi-row INSERT로 저장한다")
    void saveAllUsesSingleMultiRowInsert() {
        ChatMessage chatMessage = ChatMessage.builder().id(10L).build();
        Chatroom chatroom = Chatroom.builder().id(1L).build();
        List<ChatParticipant> participants = List.of(
                ChatParticipant.builder()
                        .user(User.builder().id(2L).build())
                        .chatroom(chatroom)
                        .build(),
                ChatParticipant.builder()
                        .user(User.builder().id(3L).build())
                        .chatroom(chatroom)
                        .build()
        );
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);

        bulkRepository.saveAll(chatMessage, participants);

        verify(jdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("(?, ?, ?, ?, ?), (?, ?, ?, ?, ?)");
        assertThat(paramsCaptor.getValue()).hasSize(10);
        assertThat(paramsCaptor.getValue()[2]).isEqualTo(10L);
        assertThat(paramsCaptor.getValue()[4]).isEqualTo(2L);
        assertThat(paramsCaptor.getValue()[9]).isEqualTo(3L);
    }
}

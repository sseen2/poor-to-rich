package com.poortorich.chat.repository;

import com.poortorich.chat.entity.ChatMessage;
import com.poortorich.chat.entity.ChatParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UnreadChatMessageBulkRepository {

    private static final int BULK_INSERT_CHUNK_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;

    public void saveAll(ChatMessage chatMessage, List<ChatParticipant> chatMembers) {
        for (int start = 0; start < chatMembers.size(); start += BULK_INSERT_CHUNK_SIZE) {
            int end = Math.min(start + BULK_INSERT_CHUNK_SIZE, chatMembers.size());
            bulkInsert(chatMessage, chatMembers.subList(start, end));
        }
    }

    private void bulkInsert(ChatMessage chatMessage, List<ChatParticipant> chatMembers) {
        String sql = """
                INSERT INTO unread_chat_message
                (created_date, updated_date, chat_message_id, chatroom_id, user_id)
                VALUES %s
                """.formatted(String.join(", ", Collections.nCopies(
                chatMembers.size(), "(?, ?, ?, ?, ?)")));
        List<Object> params = new ArrayList<>(chatMembers.size() * 5);
        LocalDateTime now = LocalDateTime.now();

        for (ChatParticipant chatMember : chatMembers) {
            params.add(now);
            params.add(now);
            params.add(chatMessage.getId());
            params.add(chatMember.getChatroom().getId());
            params.add(chatMember.getUser().getId());
        }

        jdbcTemplate.update(sql, params.toArray());
    }
}

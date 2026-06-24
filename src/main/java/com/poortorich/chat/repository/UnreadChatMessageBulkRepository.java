package com.poortorich.chat.repository;

import com.poortorich.chat.entity.ChatMessage;
import com.poortorich.chat.entity.ChatParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UnreadChatMessageBulkRepository {

    private final JdbcTemplate jdbcTemplate;

    public void saveAll(ChatMessage chatMessage, List<ChatParticipant> chatMembers) {
        if (chatMembers.isEmpty()) {
            return;
        }

        StringBuilder sql = new StringBuilder("""
                INSERT INTO unread_chat_message
                (created_date, updated_date, chat_message_id, chatroom_id, user_id)
                VALUES
                """);
        List<Object> params = new ArrayList<>(chatMembers.size() * 5);
        LocalDateTime now = LocalDateTime.now();

        for (int index = 0; index < chatMembers.size(); index++) {
            if (index > 0) {
                sql.append(", ");
            }
            sql.append("(?, ?, ?, ?, ?)");

            ChatParticipant chatMember = chatMembers.get(index);
            params.add(now);
            params.add(now);
            params.add(chatMessage.getId());
            params.add(chatMember.getChatroom().getId());
            params.add(chatMember.getUser().getId());
        }

        jdbcTemplate.update(sql.toString(), params.toArray());
    }
}

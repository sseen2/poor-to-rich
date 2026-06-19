package com.poortorich.chat.repository;

import com.poortorich.chat.entity.ChatMessage;
import com.poortorich.chat.entity.enums.ChatMessageType;
import com.poortorich.chat.entity.enums.MessageType;
import com.poortorich.ranking.entity.Ranking;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatMessageBulkRepository {

    private static final int BULK_INSERT_CHUNK_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final ChatMessageRepository chatMessageRepository;

    public List<ChatMessage> saveRankingMessages(List<Ranking> rankings) {
        if (rankings == null || rankings.isEmpty()) {
            return List.of();
        }

        List<Long> savedIds = new ArrayList<>(rankings.size());
        for (int start = 0; start < rankings.size(); start += BULK_INSERT_CHUNK_SIZE) {
            int end = Math.min(start + BULK_INSERT_CHUNK_SIZE, rankings.size());
            savedIds.addAll(bulkInsertRankingMessages(rankings.subList(start, end)));
        }

        return chatMessageRepository.findAllByIdInOrderByIdAsc(savedIds);
    }

    private List<Long> bulkInsertRankingMessages(List<Ranking> rankings) {
        StringBuilder sql = new StringBuilder("""
                INSERT INTO chat_message
                (message_type, type, ranking_id, chatroom_id, sent_at, is_deleted)
                VALUES
                """);
        List<Object> params = new ArrayList<>(rankings.size() * 6);
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < rankings.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("(?, ?, ?, ?, ?, ?)");

            Ranking ranking = rankings.get(i);
            params.add(MessageType.RANKING.name());
            params.add(ChatMessageType.RANKING_MESSAGE.name());
            params.add(ranking.getId());
            params.add(ranking.getChatroom().getId());
            params.add(now);
            params.add(Boolean.FALSE);
        }

        jdbcTemplate.update(sql.toString(), params.toArray());

        Long firstInsertedId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (firstInsertedId == null) {
            return List.of();
        }

        return buildInsertedIds(firstInsertedId, rankings.size());
    }

    private List<Long> buildInsertedIds(Long firstInsertedId, int insertedCount) {
        List<Long> ids = new ArrayList<>(insertedCount);
        for (long offset = 0; offset < insertedCount; offset++) {
            ids.add(firstInsertedId + offset);
        }
        return ids;
    }
}

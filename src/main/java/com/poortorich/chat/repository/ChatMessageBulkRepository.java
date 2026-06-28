package com.poortorich.chat.repository;

import com.poortorich.chat.entity.ChatMessage;
import com.poortorich.chat.entity.enums.ChatMessageType;
import com.poortorich.chat.entity.enums.MessageType;
import com.poortorich.ranking.entity.Ranking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ChatMessageBulkRepository {

    private static final int BULK_INSERT_CHUNK_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final ChatMessageRepository chatMessageRepository;

    public List<ChatMessage> saveRankingMessages(List<Ranking> rankings) {
        List<Long> savedIds = new ArrayList<>(rankings.size());
        for (int start = 0; start < rankings.size(); start += BULK_INSERT_CHUNK_SIZE) {
            int end = Math.min(start + BULK_INSERT_CHUNK_SIZE, rankings.size());
            savedIds.addAll(bulkInsertRankingMessages(rankings.subList(start, end)));
        }

        return chatMessageRepository.findAllByIdInOrderByIdAsc(savedIds);
    }

    private List<Long> bulkInsertRankingMessages(List<Ranking> rankings) {
        String sql = """
                INSERT INTO chat_message
                (message_type, type, ranking_id, chatroom_id, sent_at, is_deleted)
                VALUES %s
                """.formatted(String.join(", ", Collections.nCopies(
                rankings.size(), "(?, ?, ?, ?, ?, ?)")));
        List<Object> params = new ArrayList<>(rankings.size() * 6);
        LocalDateTime now = LocalDateTime.now();

        for (Ranking ranking : rankings) {
            params.add(MessageType.RANKING.name());
            params.add(ChatMessageType.RANKING_MESSAGE.name());
            params.add(ranking.getId());
            params.add(ranking.getChatroom().getId());
            params.add(now);
            params.add(Boolean.FALSE);
        }

        long startedAt = System.nanoTime();
        jdbcTemplate.update(sql, params.toArray());
        log.info(
                "[RANKING_MESSAGE_BULK_INSERT] rows={}, elapsedMs={}",
                rankings.size(),
                (System.nanoTime() - startedAt) / 1_000_000.0
        );

        Long firstInsertedId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (firstInsertedId == null) {
            return List.of();
        }

        return LongStream.range(firstInsertedId, firstInsertedId + rankings.size())
                .boxed()
                .toList();
    }
}

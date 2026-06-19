package com.poortorich.ranking.repository;

import com.poortorich.ranking.entity.Ranking;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RankingBulkRepository {

    private static final int BULK_INSERT_CHUNK_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final RankingRepository rankingRepository;

    public List<Ranking> saveAll(List<Ranking> rankings) {
        if (rankings == null || rankings.isEmpty()) {
            return List.of();
        }

        List<Long> savedIds = new ArrayList<>(rankings.size());
        for (int start = 0; start < rankings.size(); start += BULK_INSERT_CHUNK_SIZE) {
            int end = Math.min(start + BULK_INSERT_CHUNK_SIZE, rankings.size());
            savedIds.addAll(bulkInsert(rankings.subList(start, end)));
        }

        return rankingRepository.findAllByIdInOrderByIdAsc(savedIds);
    }

    private List<Long> bulkInsert(List<Ranking> rankings) {
        StringBuilder sql = new StringBuilder("""
                INSERT INTO ranking
                (saver_first, saver_second, saver_third, flexer_first, flexer_second, flexer_third,
                 created_date, updated_date, chatroom_id)
                VALUES
                """);
        List<Object> params = new ArrayList<>(rankings.size() * 9);
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < rankings.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("(?, ?, ?, ?, ?, ?, ?, ?, ?)");

            Ranking ranking = rankings.get(i);
            params.add(ranking.getSaverFirst());
            params.add(ranking.getSaverSecond());
            params.add(ranking.getSaverThird());
            params.add(ranking.getFlexerFirst());
            params.add(ranking.getFlexerSecond());
            params.add(ranking.getFlexerThird());
            params.add(now);
            params.add(now);
            params.add(ranking.getChatroom().getId());
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

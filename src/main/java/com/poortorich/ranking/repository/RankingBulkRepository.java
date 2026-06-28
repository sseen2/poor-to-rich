package com.poortorich.ranking.repository;

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
public class RankingBulkRepository {

    private static final int BULK_INSERT_CHUNK_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final RankingRepository rankingRepository;

    public List<Ranking> saveAll(List<Ranking> rankings) {
        List<Long> savedIds = new ArrayList<>(rankings.size());
        for (int start = 0; start < rankings.size(); start += BULK_INSERT_CHUNK_SIZE) {
            int end = Math.min(start + BULK_INSERT_CHUNK_SIZE, rankings.size());
            savedIds.addAll(bulkInsert(rankings.subList(start, end)));
        }

        return rankingRepository.findAllByIdInOrderByIdAsc(savedIds);
    }

    private List<Long> bulkInsert(List<Ranking> rankings) {
        String sql = """
                INSERT INTO ranking
                (saver_first, saver_second, saver_third, flexer_first, flexer_second, flexer_third,
                 created_date, updated_date, chatroom_id)
                VALUES %s
                """.formatted(String.join(", ", Collections.nCopies(
                rankings.size(), "(?, ?, ?, ?, ?, ?, ?, ?, ?)")));
        List<Object> params = new ArrayList<>(rankings.size() * 9);
        LocalDateTime now = LocalDateTime.now();

        for (Ranking ranking : rankings) {
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

        long startedAt = System.nanoTime();
        jdbcTemplate.update(sql, params.toArray());
        log.info(
                "[RANKING_BULK_INSERT] rows={}, elapsedMs={}",
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

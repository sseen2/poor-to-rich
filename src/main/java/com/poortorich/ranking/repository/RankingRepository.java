package com.poortorich.ranking.repository;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.ranking.entity.Ranking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RankingRepository extends JpaRepository<Ranking, Long> {

    Optional<Ranking> findFirstByChatroomAndCreatedDateBetweenOrderByCreatedDateDesc(
            Chatroom chatroom,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    @Query("""
                SELECT r
                  FROM Ranking r
                 WHERE r.chatroom = :chatroom
                   AND function('date', r.createdDate) IN :mondays
                 ORDER BY r.createdDate DESC
            """)
    List<Ranking> findAllByChatroomWithDateIn(Chatroom chatroom, List<LocalDate> mondays);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Ranking r WHERE r.chatroom = :chatroom")
    void deleteByChatroom(@Param("chatroom") Chatroom chatroom);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Ranking r WHERE r.createdDate > :since")
    void deleteByCreatedDateAfter(@Param("since") LocalDateTime since);
}

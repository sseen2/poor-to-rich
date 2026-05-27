package com.poortorich.chat.repository;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.entity.ChatroomSummary;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatroomSummaryRepository extends JpaRepository<ChatroomSummary, Long> {

    Optional<ChatroomSummary> findByChatroom(Chatroom chatroom);

    List<ChatroomSummary> findByChatroom_IdIn(List<Long> chatroomIds);

    void deleteByChatroom(Chatroom chatroom);

    @Query("""
            SELECT cs
            FROM ChatroomSummary cs
            JOIN FETCH cs.chatroom c
            WHERE cs.isClosed = false
            AND (:cursorChatroomId IS NULL OR c.id < :cursorChatroomId)
            ORDER BY c.id DESC
            """)
    List<ChatroomSummary> findByCreatedAtCursor(
            @Param("cursorChatroomId") Long cursorChatroomId,
            Pageable pageable
    );

    @Query("""
            SELECT cs
            FROM ChatroomSummary cs
            JOIN FETCH cs.chatroom c
            WHERE cs.isClosed = false
            AND (:cursorLastMessageAt IS NULL
                OR cs.lastMessageAt < :cursorLastMessageAt
                OR (cs.lastMessageAt = :cursorLastMessageAt AND c.id < :cursorChatroomId))
            ORDER BY cs.lastMessageAt DESC, c.id DESC
            """)
    List<ChatroomSummary> findByUpdatedAtCursor(
            @Param("cursorLastMessageAt") LocalDateTime cursorLastMessageAt,
            @Param("cursorChatroomId") Long cursorChatroomId,
            Pageable pageable
    );

    @Query("""
            SELECT cs
            FROM ChatroomSummary cs
            JOIN FETCH cs.chatroom c
            WHERE cs.isClosed = false
            AND (:cursorLikeCount IS NULL
                OR cs.likeCount < :cursorLikeCount
                OR (cs.likeCount = :cursorLikeCount AND cs.participantCount < :cursorParticipantCount)
                OR (cs.likeCount = :cursorLikeCount
                    AND cs.participantCount = :cursorParticipantCount
                    AND c.id < :cursorChatroomId))
            ORDER BY cs.likeCount DESC, cs.participantCount DESC, c.id DESC
            """)
    List<ChatroomSummary> findByLikeCursor(
            @Param("cursorLikeCount") Long cursorLikeCount,
            @Param("cursorParticipantCount") Long cursorParticipantCount,
            @Param("cursorChatroomId") Long cursorChatroomId,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChatroomSummary cs
            SET cs.lastMessageAt = :lastMessageAt
            WHERE cs.chatroom.id = :chatroomId
            """)
    void updateLastMessageAt(
            @Param("chatroomId") Long chatroomId,
            @Param("lastMessageAt") LocalDateTime lastMessageAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChatroomSummary cs
            SET cs.likeCount = :likeCount
            WHERE cs.chatroom.id = :chatroomId
            """)
    void updateLikeCount(
            @Param("chatroomId") Long chatroomId,
            @Param("likeCount") Long likeCount
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChatroomSummary cs
            SET cs.participantCount = :participantCount
            WHERE cs.chatroom.id = :chatroomId
            """)
    void updateParticipantCount(
            @Param("chatroomId") Long chatroomId,
            @Param("participantCount") Long participantCount
    );
}

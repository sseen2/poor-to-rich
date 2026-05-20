package com.poortorich.chat.repository;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.entity.enums.ChatroomRole;
import com.poortorich.chat.model.ChatroomListProjection;
import com.poortorich.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatroomRepository extends JpaRepository<Chatroom, Long> {

    List<Chatroom> findAllByIdInAndIsClosedFalse(List<Long> chatroomIds);

    @Query(value = """
            SELECT
                c.id AS chatroomId,
                COALESCE(DATE_FORMAT(lm.last_message_at, '%Y-%m-%dT%H:%i:%s.%f'), '') AS lastMessageTime,
                DATE_FORMAT(c.created_date, '%Y-%m-%dT%H:%i:%s.%f') AS cursorDateTime,
                0 AS likeCount,
                0 AS participantCount
            FROM chatroom c
            LEFT JOIN (
                SELECT cm.chatroom_id, MAX(cm.sent_at) AS last_message_at
                FROM chat_message cm
                WHERE cm.is_deleted = false
                AND cm.type IN ('CHAT_MESSAGE', 'RANKING_MESSAGE')
                GROUP BY cm.chatroom_id
            ) lm ON lm.chatroom_id = c.id
            WHERE c.is_closed = false
            AND (:cursorChatroomId IS NULL OR c.id < :cursorChatroomId)
            ORDER BY c.id DESC
            LIMIT :size
            """, nativeQuery = true)
    List<ChatroomListProjection> findChatroomsByCreatedAtCursor(
            @Param("cursorChatroomId") Long cursorChatroomId,
            @Param("size") int size
    );

    @Query(value = """
            SELECT
                c.id AS chatroomId,
                COALESCE(DATE_FORMAT(lm.last_message_at, '%Y-%m-%dT%H:%i:%s.%f'), '') AS lastMessageTime,
                DATE_FORMAT(COALESCE(lm.last_message_at, c.created_date), '%Y-%m-%dT%H:%i:%s.%f') AS cursorDateTime,
                0 AS likeCount,
                0 AS participantCount
            FROM chatroom c
            LEFT JOIN (
                SELECT cm.chatroom_id, MAX(cm.sent_at) AS last_message_at
                FROM chat_message cm
                WHERE cm.is_deleted = false
                AND cm.type IN ('CHAT_MESSAGE', 'RANKING_MESSAGE')
                GROUP BY cm.chatroom_id
            ) lm ON lm.chatroom_id = c.id
            WHERE c.is_closed = false
            AND (:cursorDateTime IS NULL
                OR COALESCE(lm.last_message_at, c.created_date) < :cursorDateTime
                OR (COALESCE(lm.last_message_at, c.created_date) = :cursorDateTime
                    AND c.id < :cursorChatroomId))
            ORDER BY COALESCE(lm.last_message_at, c.created_date) DESC, c.id DESC
            LIMIT :size
            """, nativeQuery = true)
    List<ChatroomListProjection> findChatroomsByUpdatedAtCursor(
            @Param("cursorDateTime") String cursorDateTime,
            @Param("cursorChatroomId") Long cursorChatroomId,
            @Param("size") int size
    );

    @Query(value = """
            SELECT
                c.id AS chatroomId,
                COALESCE(DATE_FORMAT(lm.last_message_at, '%Y-%m-%dT%H:%i:%s.%f'), '') AS lastMessageTime,
                DATE_FORMAT(COALESCE(lm.last_message_at, c.created_date), '%Y-%m-%dT%H:%i:%s.%f') AS cursorDateTime,
                COALESCE(lc.like_count, 0) AS likeCount,
                COALESCE(pc.participant_count, 0) AS participantCount
            FROM chatroom c
            LEFT JOIN (
                SELECT cm.chatroom_id, MAX(cm.sent_at) AS last_message_at
                FROM chat_message cm
                WHERE cm.is_deleted = false
                AND cm.type IN ('CHAT_MESSAGE', 'RANKING_MESSAGE')
                GROUP BY cm.chatroom_id
            ) lm ON lm.chatroom_id = c.id
            LEFT JOIN (
                SELECT l.chatroom_id, COUNT(*) AS like_count
                FROM likes l
                WHERE l.like_status = true
                GROUP BY l.chatroom_id
            ) lc ON lc.chatroom_id = c.id
            LEFT JOIN (
                SELECT cp.chatroom_id, COUNT(*) AS participant_count
                FROM chat_participant cp
                WHERE cp.is_participated = true
                AND cp.role <> 'BANNED'
                GROUP BY cp.chatroom_id
            ) pc ON pc.chatroom_id = c.id
            WHERE c.is_closed = false
            AND (:cursorLikeCount IS NULL
                OR COALESCE(lc.like_count, 0) < :cursorLikeCount
                OR (COALESCE(lc.like_count, 0) = :cursorLikeCount
                    AND COALESCE(pc.participant_count, 0) < :cursorParticipantCount)
                OR (COALESCE(lc.like_count, 0) = :cursorLikeCount
                    AND COALESCE(pc.participant_count, 0) = :cursorParticipantCount
                    AND c.id < :cursorChatroomId))
            ORDER BY COALESCE(lc.like_count, 0) DESC, COALESCE(pc.participant_count, 0) DESC, c.id DESC
            LIMIT :size
            """, nativeQuery = true)
    List<ChatroomListProjection> findChatroomsByLikeCursor(
            @Param("cursorLikeCount") Long cursorLikeCount,
            @Param("cursorParticipantCount") Long cursorParticipantCount,
            @Param("cursorChatroomId") Long cursorChatroomId,
            @Param("size") int size
    );

    @Query("""
                SELECT c
                  FROM Chatroom c
                  JOIN ChatParticipant cp
                    ON cp.chatroom = c
                WHERE c.isClosed = false
                   AND cp.user = :user
                   AND cp.role = :role
            """)
    List<Chatroom> findChatroomByUserAndRole(@Param("user") User user, @Param("role") ChatroomRole role);

    @Query("""
                SELECT DISTINCT c
                  FROM Chatroom c
                  LEFT JOIN Tag t ON t.chatroom = c
                 INNER JOIN ChatParticipant cp ON cp.chatroom = c AND cp.role = 'HOST'
                WHERE c.isClosed = false
                  AND :keyword <> ''
                  AND (
                         c.title LIKE CONCAT('%', :keyword, '%')
                      OR c.description LIKE CONCAT('%', :keyword, '%')
                      OR cp.user.nickname LIKE CONCAT('%', :keyword, '%')
                      OR (t.name IS NOT NULL AND t.name LIKE CONCAT('%', :keyword, '%'))
                  )
                GROUP BY c.id
            """)
    List<Chatroom> searchChatrooms(String keyword);

    @Query(value = """
            SELECT c.id
            FROM chatroom c
            JOIN chat_participant cp ON cp.chatroom_id = c.id
            WHERE cp.user_id = :userId
            AND cp.is_participated = true
            ORDER BY c.id ASC
            LIMIT 1
            """, nativeQuery = true)
    Long findFirstChatroomIdByUser(@Param("userId") Long userId);

    List<Chatroom> findAllByIsRankingEnabledIsTrueAndIsClosedIsFalse();
}

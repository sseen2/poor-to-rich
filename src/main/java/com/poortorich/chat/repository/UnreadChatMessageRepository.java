package com.poortorich.chat.repository;

import com.poortorich.chat.entity.ChatMessage;
import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.entity.UnreadChatMessage;
import com.poortorich.chat.entity.enums.ChatroomRole;
import com.poortorich.chat.model.UnreadChatInfo;
import com.poortorich.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnreadChatMessageRepository extends JpaRepository<UnreadChatMessage, Long> {

    @Query("""
            SELECT u
            FROM UnreadChatMessage u
            JOIN u.user user
            JOIN ChatParticipant cp ON cp.user = user AND cp.chatroom = u.chatroom
            WHERE u.chatMessage = :chatMessage
            AND cp.role != :role
            """)
    List<UnreadChatMessage> findAllByChatMessageExcludingChatroomRole(
            @Param("chatMessage") ChatMessage chatMessage,
            @Param("role") ChatroomRole role);

    @Query("""
            SELECT u.chatMessage.id, u.user.id
            FROM UnreadChatMessage u
            JOIN ChatParticipant cp ON cp.user = u.user AND cp.chatroom = u.chatroom
            WHERE u.chatMessage.id IN :chatMessageIds
            AND u.user.id <> :userId
            AND cp.role != :role
            """)
    List<Object[]> findUserIdsByChatMessageIdsExcludingChatroomRole(
            @Param("userId") Long userId,
            @Param("chatMessageIds") List<Long> chatMessageIds,
            @Param("role") ChatroomRole role);

    @Query("""
            SELECT MAX(u.chatMessage.id)
            FROM UnreadChatMessage u
            WHERE u.chatroom = :chatroom
            AND u.user = :user
            """)
    Long findLastUnreadMessageId(@Param("chatroom") Chatroom chatroom, @Param("user") User user);

    @Query("""
            SELECT new com.poortorich.chat.model.UnreadChatInfo(
                u.chatroom.id,
                u.user.id,
                MAX(u.chatMessage.id)
            )
            FROM UnreadChatMessage u
            WHERE u.chatroom IN :chatrooms
            AND u.user IN :users
            GROUP BY u.chatroom, u.user
            """)
    List<UnreadChatInfo> findLastUnreadMessageIds(@Param("chatrooms") List<Chatroom> chatrooms, @Param("users") List<User> user);

    @Query("""
            SELECT u.user.id, COUNT(u)
            FROM UnreadChatMessage u
            WHERE u.chatroom = :chatroom
            GROUP BY u.user.id
            """)
    List<Object[]> countByChatroomGroupByUser(@Param("chatroom") Chatroom chatroom);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM UnreadChatMessage u
            WHERE u.chatroom = :chatroom
            AND u.user = :user
            """)
    void markMessagesAsRead(@Param("chatroom") Chatroom chatroom, @Param("user") User user);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM UnreadChatMessage u
            WHERE u.chatroom IN :chatrooms
            AND u.user IN :users
            """)
    void markAllMessageAsRead(@Param("chatrooms") List<Chatroom> chatrooms, @Param("users") List<User> users);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UnreadChatMessage u WHERE u.chatroom = :chatroom")
    void deleteByChatroom(@Param("chatroom") Chatroom chatroom);

    Long countByUserAndChatroom(User user, Chatroom chatroom);
}

package com.poortorich.like.repository;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.like.entity.Like;
import com.poortorich.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByUserAndChatroom(User user, Chatroom chatroom);

    Long countByChatroomAndLikeStatusTrue(Chatroom chatroom);

    @Query("""
            SELECT l.chatroom.id, COUNT(l)
            FROM Like l
            WHERE l.chatroom.id IN :chatroomIds
            AND l.likeStatus = true
            GROUP BY l.chatroom.id
            """)
    List<Object[]> countByChatroomIds(@Param("chatroomIds") List<Long> chatroomIds);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Like l WHERE l.chatroom = :chatroom")
    void deleteByChatroom(@Param("chatroom") Chatroom chatroom);
}

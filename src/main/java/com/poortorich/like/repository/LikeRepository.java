package com.poortorich.like.repository;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.like.entity.Like;
import com.poortorich.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByUserAndChatroom(User user, Chatroom chatroom);

    Long countByChatroomAndLikeStatusTrue(Chatroom chatroom);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Like l WHERE l.chatroom = :chatroom")
    void deleteByChatroom(@Param("chatroom") Chatroom chatroom);
}

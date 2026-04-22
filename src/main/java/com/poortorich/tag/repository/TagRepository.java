package com.poortorich.tag.repository;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByChatroom(Chatroom chatroom);

    @Query("SELECT t FROM Tag t WHERE t.chatroom.id IN :chatroomIds")
    List<Tag> findByChatroomIds(@Param("chatroomIds") List<Long> chatroomIds);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Tag t WHERE t.chatroom = :chatroom")
    void deleteByChatroom(@Param("chatroom") Chatroom chatroom);
}

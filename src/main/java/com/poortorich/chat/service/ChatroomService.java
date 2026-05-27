package com.poortorich.chat.service;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.entity.enums.ChatroomRole;
import com.poortorich.chat.model.ChatroomContext;
import com.poortorich.chat.repository.ChatroomRepository;
import com.poortorich.chat.request.ChatroomCreateRequest;
import com.poortorich.chat.request.enums.SortBy;
import com.poortorich.chat.response.enums.ChatResponse;
import com.poortorich.chat.util.ChatBuilder;
import com.poortorich.global.exceptions.NotFoundException;
import com.poortorich.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatroomService {

    private final ChatroomSummaryService chatroomSummaryService;
    private final ChatroomRepository chatroomRepository;

    private final ChatBuilder chatBuilder;

    public Chatroom createChatroom(String imageUrl, ChatroomCreateRequest request) {
        Chatroom chatroom = chatBuilder.buildChatroom(imageUrl, request);
        return chatroomRepository.save(chatroom);
    }

    public ChatroomContext getAllChatrooms(SortBy sortBy, String cursor) {
        return chatroomSummaryService.getChatroomContext(sortBy, cursor, 20);
    }

    public Chatroom findById(Long chatroomId) {
        return chatroomRepository.findById(chatroomId)
                .orElseThrow(() -> new NotFoundException(ChatResponse.CHATROOM_NOT_FOUND));
    }

    public List<Chatroom> searchChatrooms(String keyword) {
        return chatroomRepository.searchChatrooms(keyword);
    }

    public List<Chatroom> getHostedChatrooms(User user) {
        return chatroomRepository.findChatroomByUserAndRole(user, ChatroomRole.HOST);
    }

    @Transactional
    public void closeChatroomById(Long chatroomId) {
        Chatroom chatroom = chatroomRepository.findById(chatroomId)
                .orElseThrow(() -> new NotFoundException(ChatResponse.CHATROOM_NOT_FOUND));
        chatroom.closeChatroom();
        chatroomSummaryService.closeSummary(chatroom);
    }

    @Transactional
    public void deleteById(Long chatroomId) {
        Chatroom chatroom = findById(chatroomId);
        chatroomSummaryService.deleteByChatroom(chatroom);
        chatroomRepository.delete(chatroom);
    }

    public Long getFirstChatroomIdByUser(User user) {
        return chatroomRepository.findFirstChatroomIdByUser(user.getId());
    }

    public List<Chatroom> getChatroomsByRankingEnabledIsTrue() {
        return chatroomRepository.findAllByIsRankingEnabledIsTrueAndIsClosedIsFalse();
    }
}

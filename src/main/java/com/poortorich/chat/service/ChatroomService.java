package com.poortorich.chat.service;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.entity.enums.ChatroomRole;
import com.poortorich.chat.repository.ChatroomRepository;
import com.poortorich.chat.repository.RedisChatRepository;
import com.poortorich.chat.request.ChatroomCreateRequest;
import com.poortorich.chat.request.enums.SortBy;
import com.poortorich.chat.response.enums.ChatResponse;
import com.poortorich.chat.util.ChatBuilder;
import com.poortorich.global.exceptions.NotFoundException;
import com.poortorich.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatroomService {

    private final ChatMessageService chatMessageService;
    private final ChatroomRepository chatroomRepository;
    private final RedisChatRepository redisChatRepository;

    private final ChatBuilder chatBuilder;

    public Chatroom createChatroom(String imageUrl, ChatroomCreateRequest request) {
        Chatroom chatroom = chatBuilder.buildChatroom(imageUrl, request);
        return chatroomRepository.save(chatroom);
    }

    public void saveNewVersionChatroomsInRedis() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    saveNewVersion();
                }
            });
        } else {
            saveNewVersion();
        }
    }

    public Long getVersion(Long version) {
        if (version == -1L) {
            version = redisChatRepository.getCurrentVersion();
        }

        return version;
    }

    public List<Chatroom> getAllChatrooms(SortBy sortBy, Long cursor, Long version) {
        if (redisChatRepository.existsBySortBy(sortBy, version)) {
            return findByIds(redisChatRepository.getChatroomIds(sortBy, cursor, version, 20));
        }

        saveNewVersion();

        return findByIds(redisChatRepository.getChatroomIds(sortBy, cursor, version, 20));
    }

    private void saveNewVersion() {
        Long newVersion = System.currentTimeMillis();

        saveNewVersionChatroomsInRedis(SortBy.UPDATED_AT, newVersion);
        saveNewVersionChatroomsInRedis(SortBy.LIKE, newVersion);
        saveNewVersionChatroomsInRedis(SortBy.CREATED_AT, newVersion);

        redisChatRepository.updateCurrentVersion(newVersion);
    }

    private void saveNewVersionChatroomsInRedis(SortBy sortBy, Long newVersion) {
        List<Long> chatrooms = getChatroomIdsBySortBy(sortBy);
        List<String> lastMessageTimes = getLastMessageTimes(chatrooms);

        if (!chatrooms.isEmpty()) {
            redisChatRepository.saveNewVersion(sortBy, chatrooms, lastMessageTimes, newVersion);
        }
    }

    public List<String> getAllLastMessageTimes(SortBy sortBy, Long cursor, Long version) {
        return redisChatRepository.getLastMessageTimes(sortBy, cursor, version, 20);
    }

    private List<Long> getChatroomIdsBySortBy(SortBy sortBy) {
        return getBySortBy(sortBy).stream()
                .map(Chatroom::getId)
                .toList();
    }

    private List<String> getLastMessageTimes(List<Long> chatroomIds) {
        return chatroomIds.stream()
                .map(chatroomId -> {
                    Chatroom chatroom = findById(chatroomId);
                    return chatMessageService.getLastMessageTime(chatroom);
                })
                .toList();
    }

    private List<Chatroom> getBySortBy(SortBy sortBy) {
        if (sortBy.equals(SortBy.LIKE)) {
            return chatroomRepository.findChatroomsSortByLike();
        }

        if (sortBy.equals(SortBy.UPDATED_AT)) {
            return chatroomRepository.findChatroomsSortByUpdatedAt();
        }

        return chatroomRepository.findChatroomsByCreatedAt();
    }

    private List<Chatroom> findByIds(List<Long> chatroomIds) {
        List<Chatroom> chatrooms = chatroomRepository.findAllByIdInAndIsClosedFalse(chatroomIds);
        chatrooms.sort(Comparator.comparingInt(c -> chatroomIds.indexOf(c.getId())));
        return chatrooms;
    }

    public Boolean hasNext(SortBy sortBy, Long lastChatroomId, Long version) {
        return redisChatRepository.hasNext(sortBy, lastChatroomId, version);
    }

    public Long getNextCursor(SortBy sortBy, Long lastChatroomId, Long version) {
        return redisChatRepository.getNextCursor(sortBy, lastChatroomId, version);
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
        chatroomRepository.findById(chatroomId)
                .orElseThrow(() -> new NotFoundException(ChatResponse.CHATROOM_NOT_FOUND))
                .closeChatroom();
    }

    @Transactional
    public void deleteById(Long chatroomId) {
        chatroomRepository.deleteById(chatroomId);
    }

    public Long getFirstChatroomIdByUser(User user) {
        return chatroomRepository.findFirstChatroomIdByUser(user.getId());
    }

    public List<Chatroom> getChatroomsByRankingEnabledIsTrue() {
        return chatroomRepository.findAllByIsRankingEnabledIsTrueAndIsClosedIsFalse();
    }
}

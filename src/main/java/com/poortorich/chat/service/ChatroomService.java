package com.poortorich.chat.service;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.entity.enums.ChatroomRole;
import com.poortorich.chat.model.ChatroomContext;
import com.poortorich.chat.model.ChatroomListProjection;
import com.poortorich.chat.repository.ChatroomRepository;
import com.poortorich.chat.request.ChatroomCreateRequest;
import com.poortorich.chat.request.enums.SortBy;
import com.poortorich.chat.response.enums.ChatResponse;
import com.poortorich.chat.util.ChatBuilder;
import com.poortorich.global.exceptions.BadRequestException;
import com.poortorich.global.exceptions.NotFoundException;
import com.poortorich.user.entity.User;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatroomService {

    private static final String CURSOR_DELIMITER = ",";
    private static final int CHATROOM_PAGE_SIZE = 20;

    private final ChatroomRepository chatroomRepository;

    private final ChatBuilder chatBuilder;

    public Chatroom createChatroom(String imageUrl, ChatroomCreateRequest request) {
        Chatroom chatroom = chatBuilder.buildChatroom(imageUrl, request);
        return chatroomRepository.save(chatroom);
    }

    public ChatroomContext getAllChatrooms(SortBy sortBy, String cursor) {
        List<ChatroomListProjection> rows = getChatroomRows(sortBy, cursor, CHATROOM_PAGE_SIZE + 1);
        boolean hasNext = rows.size() > CHATROOM_PAGE_SIZE;

        if (hasNext) {
            rows = rows.subList(0, CHATROOM_PAGE_SIZE);
        }

        return ChatroomContext.builder()
                .chatroomIds(rows.stream()
                        .map(ChatroomListProjection::getChatroomId)
                        .toList())
                .lastMessageTimes(rows.stream()
                        .map(ChatroomListProjection::getLastMessageTime)
                        .toList())
                .hasNext(hasNext)
                .nextCursor(getNextCursor(sortBy, rows))
                .build();
    }

    private List<ChatroomListProjection> getChatroomRows(SortBy sortBy, String cursor, int size) {
        return switch (sortBy) {
            case CREATED_AT -> chatroomRepository.findChatroomsByCreatedAtCursor(parseCreatedAtCursor(cursor), size);
            case UPDATED_AT -> {
                UpdatedAtCursor parsedCursor = parseUpdatedAtCursor(cursor);
                yield chatroomRepository.findChatroomsByUpdatedAtCursor(
                        parsedCursor.cursorDateTime(),
                        parsedCursor.chatroomId(),
                        size
                );
            }
            case LIKE -> {
                LikeCursor parsedCursor = parseLikeCursor(cursor);
                yield chatroomRepository.findChatroomsByLikeCursor(
                        parsedCursor.likeCount(),
                        parsedCursor.participantCount(),
                        parsedCursor.chatroomId(),
                        size
                );
            }
        };
    }

    private String getNextCursor(SortBy sortBy, List<ChatroomListProjection> rows) {
        if (rows.isEmpty()) {
            return null;
        }

        ChatroomListProjection lastRow = rows.getLast();
        return switch (sortBy) {
            case CREATED_AT -> lastRow.getChatroomId().toString();
            case UPDATED_AT -> lastRow.getCursorDateTime() + CURSOR_DELIMITER + lastRow.getChatroomId();
            case LIKE -> lastRow.getLikeCount() + CURSOR_DELIMITER
                    + lastRow.getParticipantCount() + CURSOR_DELIMITER
                    + lastRow.getChatroomId();
        };
    }

    private Long parseCreatedAtCursor(String cursor) {
        if (isFirstPageCursor(cursor)) {
            return null;
        }

        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException exception) {
            throw new BadRequestException(ChatResponse.CURSOR_INVALID);
        }
    }

    private UpdatedAtCursor parseUpdatedAtCursor(String cursor) {
        if (isFirstPageCursor(cursor)) {
            return new UpdatedAtCursor(null, null);
        }

        String[] parts = cursor.split(CURSOR_DELIMITER);
        if (parts.length != 2) {
            throw new BadRequestException(ChatResponse.CURSOR_INVALID);
        }

        try {
            return new UpdatedAtCursor(parts[0], Long.parseLong(parts[1]));
        } catch (NumberFormatException exception) {
            throw new BadRequestException(ChatResponse.CURSOR_INVALID);
        }
    }

    private LikeCursor parseLikeCursor(String cursor) {
        if (isFirstPageCursor(cursor)) {
            return new LikeCursor(null, null, null);
        }

        String[] parts = cursor.split(CURSOR_DELIMITER);
        if (parts.length != 3) {
            throw new BadRequestException(ChatResponse.CURSOR_INVALID);
        }

        try {
            return new LikeCursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]));
        } catch (NumberFormatException exception) {
            throw new BadRequestException(ChatResponse.CURSOR_INVALID);
        }
    }

    private boolean isFirstPageCursor(String cursor) {
        return cursor == null || cursor.isBlank() || "-1".equals(cursor);
    }

    private record UpdatedAtCursor(String cursorDateTime, Long chatroomId) {
    }

    private record LikeCursor(Long likeCount, Long participantCount, Long chatroomId) {
    }

    public List<Chatroom> findByIds(List<Long> chatroomIds) {
        List<Chatroom> chatrooms = chatroomRepository.findAllByIdInAndIsClosedFalse(chatroomIds);
        chatrooms.sort(Comparator.comparingInt(c -> chatroomIds.indexOf(c.getId())));
        return chatrooms;
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
    }

    @Transactional
    public void deleteById(Long chatroomId) {
        Chatroom chatroom = findById(chatroomId);
        chatroomRepository.delete(chatroom);
    }

    public Long getFirstChatroomIdByUser(User user) {
        return chatroomRepository.findFirstChatroomIdByUser(user.getId());
    }

    public List<Chatroom> getChatroomsByRankingEnabledIsTrue() {
        return chatroomRepository.findAllByIsRankingEnabledIsTrueAndIsClosedIsFalse();
    }
}

package com.poortorich.chat.service;

import com.poortorich.chat.entity.ChatMessage;
import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.entity.ChatroomSummary;
import com.poortorich.chat.entity.enums.ChatMessageType;
import com.poortorich.chat.model.ChatroomContext;
import com.poortorich.chat.repository.ChatroomSummaryRepository;
import com.poortorich.chat.request.enums.SortBy;
import com.poortorich.chat.response.enums.ChatResponse;
import com.poortorich.global.exceptions.BadRequestException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatroomSummaryService {

    private static final String CURSOR_DELIMITER = ",";

    private final ChatroomSummaryRepository chatroomSummaryRepository;

    @Transactional
    public void createSummary(Chatroom chatroom, Long participantCount) {
        chatroomSummaryRepository.save(ChatroomSummary.create(chatroom, participantCount));
    }

    @Transactional(readOnly = true)
    public ChatroomContext getChatroomContext(SortBy sortBy, String cursor, int size) {
        List<ChatroomSummary> summaries = new ArrayList<>(getBySortBy(sortBy, cursor, size + 1));
        boolean hasNext = summaries.size() > size;

        if (hasNext) {
            summaries.removeLast();
        }

        return ChatroomContext.builder()
                .chatroomIds(summaries.stream()
                        .map(summary -> summary.getChatroom().getId())
                        .toList())
                .lastMessageTimes(summaries.stream()
                        .map(this::getLastMessageTime)
                        .toList())
                .hasNext(hasNext)
                .nextCursor(getNextCursor(sortBy, summaries))
                .build();
    }

    private List<ChatroomSummary> getBySortBy(SortBy sortBy, String cursor, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);

        return switch (sortBy) {
            case CREATED_AT -> chatroomSummaryRepository.findByCreatedAtCursor(
                    parseCreatedAtCursor(cursor),
                    pageRequest
            );
            case UPDATED_AT -> {
                UpdatedAtCursor parsedCursor = parseUpdatedAtCursor(cursor);
                yield chatroomSummaryRepository.findByUpdatedAtCursor(
                        parsedCursor.lastMessageAt(),
                        parsedCursor.chatroomId(),
                        pageRequest
                );
            }
            case LIKE -> {
                LikeCursor parsedCursor = parseLikeCursor(cursor);
                yield chatroomSummaryRepository.findByLikeCursor(
                        parsedCursor.likeCount(),
                        parsedCursor.participantCount(),
                        parsedCursor.chatroomId(),
                        pageRequest
                );
            }
        };
    }

    private String getNextCursor(SortBy sortBy, List<ChatroomSummary> summaries) {
        if (summaries.isEmpty()) {
            return null;
        }

        ChatroomSummary lastSummary = summaries.getLast();
        return switch (sortBy) {
            case CREATED_AT -> lastSummary.getChatroom().getId().toString();
            case UPDATED_AT -> getCursorLastMessageAt(lastSummary) + CURSOR_DELIMITER + lastSummary.getChatroom().getId();
            case LIKE -> lastSummary.getLikeCount() + CURSOR_DELIMITER
                    + lastSummary.getParticipantCount() + CURSOR_DELIMITER
                    + lastSummary.getChatroom().getId();
        };
    }

    private String getLastMessageTime(ChatroomSummary summary) {
        if (summary.getLastMessageAt() == null) {
            return "";
        }

        return summary.getLastMessageAt().toString();
    }

    private LocalDateTime getCursorLastMessageAt(ChatroomSummary summary) {
        return summary.getLastMessageAt() == null ? summary.getCreatedAt() : summary.getLastMessageAt();
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
            return new UpdatedAtCursor(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (RuntimeException exception) {
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

    private record UpdatedAtCursor(LocalDateTime lastMessageAt, Long chatroomId) {
    }

    private record LikeCursor(Long likeCount, Long participantCount, Long chatroomId) {
    }

    @Transactional
    public void updateLastMessage(ChatMessage chatMessage) {
        if (!isListOrderingMessage(chatMessage)) {
            return;
        }

        chatroomSummaryRepository.findByChatroom(chatMessage.getChatroom())
                .ifPresent(summary -> summary.updateLastMessageAt(chatMessage.getSentAt()));
    }

    private boolean isListOrderingMessage(ChatMessage chatMessage) {
        return ChatMessageType.CHAT_MESSAGE.equals(chatMessage.getType())
                || ChatMessageType.RANKING_MESSAGE.equals(chatMessage.getType());
    }

    @Transactional
    public void updateLikeCount(Chatroom chatroom, Long likeCount) {
        chatroomSummaryRepository.findByChatroom(chatroom)
                .ifPresent(summary -> summary.updateLikeCount(likeCount));
    }

    @Transactional
    public void updateParticipantCount(Chatroom chatroom, Long participantCount) {
        chatroomSummaryRepository.findByChatroom(chatroom)
                .ifPresent(summary -> summary.updateParticipantCount(participantCount));
    }

    @Transactional
    public void closeSummary(Chatroom chatroom) {
        chatroomSummaryRepository.findByChatroom(chatroom)
                .ifPresent(ChatroomSummary::close);
    }

    @Transactional
    public void deleteByChatroom(Chatroom chatroom) {
        chatroomSummaryRepository.deleteByChatroom(chatroom);
    }
}

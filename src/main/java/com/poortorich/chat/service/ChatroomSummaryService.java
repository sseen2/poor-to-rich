package com.poortorich.chat.service;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.entity.ChatroomSummary;
import com.poortorich.chat.entity.enums.ChatMessageType;
import com.poortorich.chat.repository.ChatMessageRepository;
import com.poortorich.chat.repository.ChatParticipantRepository;
import com.poortorich.chat.repository.ChatroomRepository;
import com.poortorich.chat.model.ChatroomContext;
import com.poortorich.chat.repository.ChatroomSummaryRepository;
import com.poortorich.chat.request.enums.SortBy;
import com.poortorich.chat.response.enums.ChatResponse;
import com.poortorich.global.exceptions.BadRequestException;
import com.poortorich.like.repository.LikeRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatroomSummaryService {

    private static final String CURSOR_DELIMITER = ",";
    private static final int RECONCILE_CHUNK_SIZE = 1000;
    private static final Long FIRST_CHATROOM_CURSOR = 0L;
    private static final List<ChatMessageType> LIST_ORDERING_MESSAGE_TYPES = List.of(
            ChatMessageType.CHAT_MESSAGE,
            ChatMessageType.RANKING_MESSAGE
    );

    private final ChatroomSummaryRepository chatroomSummaryRepository;
    private final ChatroomRepository chatroomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final LikeRepository likeRepository;
    private final TransactionTemplate transactionTemplate;

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
                .chatrooms(summaries.stream()
                        .map(ChatroomSummary::getChatroom)
                        .toList())
                .lastMessageTimes(summaries.stream()
                        .map(this::getLastMessageTime)
                        .toList())
                .participantCounts(summaries.stream()
                        .map(ChatroomSummary::getParticipantCount)
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

        String[] parts = splitCursor(cursor, 2);

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

        String[] parts = splitCursor(cursor, 3);

        try {
            return new LikeCursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]));
        } catch (NumberFormatException exception) {
            throw new BadRequestException(ChatResponse.CURSOR_INVALID);
        }
    }

    private boolean isFirstPageCursor(String cursor) {
        return cursor == null || cursor.isBlank() || "-1".equals(cursor);
    }

    private String[] splitCursor(String cursor, int expectedLength) {
        String[] parts = cursor.split(CURSOR_DELIMITER);
        if (parts.length != expectedLength) {
            throw new BadRequestException(ChatResponse.CURSOR_INVALID);
        }

        return parts;
    }

    private record UpdatedAtCursor(LocalDateTime lastMessageAt, Long chatroomId) {
    }

    private record LikeCursor(Long likeCount, Long participantCount, Long chatroomId) {
    }

    @Transactional
    public void updateLastMessage(Long chatroomId, ChatMessageType messageType, LocalDateTime sentAt) {
        if (!isListOrderingMessage(messageType)) {
            return;
        }

        chatroomSummaryRepository.updateLastMessageAt(chatroomId, sentAt);
    }

    private boolean isListOrderingMessage(ChatMessageType messageType) {
        return LIST_ORDERING_MESSAGE_TYPES.contains(messageType);
    }

    @Transactional
    public void updateLikeCount(Long chatroomId, Long likeCount) {
        chatroomSummaryRepository.updateLikeCount(chatroomId, likeCount);
    }

    @Transactional
    public void updateParticipantCount(Long chatroomId, Long participantCount) {
        chatroomSummaryRepository.updateParticipantCount(chatroomId, participantCount);
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

    public void reconcileSummaries() {
        Long lastChatroomId = FIRST_CHATROOM_CURSOR;
        int insertedCount = 0;
        int updatedCount = 0;

        while (true) {
            Long chunkCursor = lastChatroomId;
            ReconcileChunkResult result;

            try {
                result = transactionTemplate.execute(status -> reconcileSummaryChunk(chunkCursor));
            } catch (Exception exception) {
                log.error("채팅방 summary 정합성 보정 chunk 실패 - cursor: {}", chunkCursor, exception);
                throw exception;
            }

            if (result.isEmpty()) {
                break;
            }

            insertedCount += result.insertedCount();
            updatedCount += result.updatedCount();
            lastChatroomId = result.lastChatroomId();
        }

        log.info("채팅방 summary 정합성 보정 완료 - inserted: {}, updated: {}", insertedCount, updatedCount);
    }

    private ReconcileChunkResult reconcileSummaryChunk(Long lastChatroomId) {
        List<Chatroom> chatrooms = chatroomRepository.findByIdGreaterThanOrderByIdAsc(
                lastChatroomId,
                PageRequest.of(0, RECONCILE_CHUNK_SIZE)
        );

        if (chatrooms.isEmpty()) {
            return ReconcileChunkResult.empty(lastChatroomId);
        }

        List<Long> chatroomIds = chatrooms.stream()
                .map(Chatroom::getId)
                .toList();
        SummaryReconcileContext context = getSummaryReconcileContext(chatroomIds);
        List<ChatroomSummary> newSummaries = new ArrayList<>();
        int updatedCount = 0;

        for (Chatroom chatroom : chatrooms) {
            if (synchronizeExistingSummary(chatroom, context)) {
                updatedCount++;
                continue;
            }

            createMissingSummary(chatroom, context, newSummaries);
        }

        if (!newSummaries.isEmpty()) {
            chatroomSummaryRepository.saveAll(newSummaries);
        }

        return new ReconcileChunkResult(
                chatrooms.getLast().getId(),
                newSummaries.size(),
                updatedCount,
                false
        );
    }

    private boolean synchronizeExistingSummary(Chatroom chatroom, SummaryReconcileContext context) {
        ChatroomSummary summary = context.summaryMap().get(chatroom.getId());
        if (summary == null) {
            return false;
        }

        return synchronizeSummary(summary, context.getExpectedValues(chatroom));
    }

    private void createMissingSummary(
            Chatroom chatroom,
            SummaryReconcileContext context,
            List<ChatroomSummary> newSummaries
    ) {
        if (context.summaryMap().containsKey(chatroom.getId())) {
            return;
        }

        newSummaries.add(createReconciledSummary(chatroom, context.getExpectedValues(chatroom)));
    }

    private ChatroomSummary createReconciledSummary(Chatroom chatroom, SummaryValues expectedValues) {
        return ChatroomSummary.createForReconcile(
                chatroom,
                expectedValues.createdAt(),
                expectedValues.lastMessageAt(),
                expectedValues.likeCount(),
                expectedValues.participantCount(),
                expectedValues.isClosed()
        );
    }

    private Map<Long, ChatroomSummary> getSummaryMap(List<Long> chatroomIds) {
        return chatroomSummaryRepository.findByChatroom_IdIn(chatroomIds).stream()
                .collect(Collectors.toMap(summary -> summary.getChatroom().getId(), summary -> summary));
    }

    private SummaryReconcileContext getSummaryReconcileContext(List<Long> chatroomIds) {
        return new SummaryReconcileContext(
                getSummaryMap(chatroomIds),
                getLastMessageTimeMap(chatroomIds),
                getLikeCountMap(chatroomIds),
                getParticipantCountMap(chatroomIds)
        );
    }

    private Map<Long, LocalDateTime> getLastMessageTimeMap(List<Long> chatroomIds) {
        return toMap(
                chatMessageRepository.findLastMessageTimesByChatroomIds(chatroomIds, LIST_ORDERING_MESSAGE_TYPES),
                result -> (LocalDateTime) result[1]
        );
    }

    private Map<Long, Long> getLikeCountMap(List<Long> chatroomIds) {
        return toMap(likeRepository.countByChatroomIds(chatroomIds), result -> (Long) result[1]);
    }

    private Map<Long, Long> getParticipantCountMap(List<Long> chatroomIds) {
        return toMap(chatParticipantRepository.countParticipantsByChatroomIds(chatroomIds), result -> (Long) result[1]);
    }

    private <T> Map<Long, T> toMap(List<Object[]> rows, Function<Object[], T> valueMapper) {
        return rows.stream()
                .collect(Collectors.toMap(
                        result -> (Long) result[0],
                        valueMapper
                ));
    }

    private boolean synchronizeSummary(ChatroomSummary summary, SummaryValues expectedValues) {
        boolean updated = false;

        if (!Objects.equals(summary.getCreatedAt(), expectedValues.createdAt())) {
            summary.updateCreatedAt(expectedValues.createdAt());
            updated = true;
        }

        if (!Objects.equals(summary.getLastMessageAt(), expectedValues.lastMessageAt())) {
            summary.updateLastMessageAt(expectedValues.lastMessageAt());
            updated = true;
        }

        if (!Objects.equals(summary.getLikeCount(), expectedValues.likeCount())) {
            summary.updateLikeCount(expectedValues.likeCount());
            updated = true;
        }

        if (!Objects.equals(summary.getParticipantCount(), expectedValues.participantCount())) {
            summary.updateParticipantCount(expectedValues.participantCount());
            updated = true;
        }

        if (!Objects.equals(summary.getIsClosed(), expectedValues.isClosed())) {
            summary.updateClosedStatus(expectedValues.isClosed());
            updated = true;
        }

        return updated;
    }

    private record SummaryReconcileContext(
            Map<Long, ChatroomSummary> summaryMap,
            Map<Long, LocalDateTime> lastMessageTimeMap,
            Map<Long, Long> likeCountMap,
            Map<Long, Long> participantCountMap
    ) {
        private SummaryValues getExpectedValues(Chatroom chatroom) {
            Long chatroomId = chatroom.getId();
            LocalDateTime createdAt = chatroom.getCreatedDate();

            return new SummaryValues(
                    createdAt,
                    lastMessageTimeMap.getOrDefault(chatroomId, createdAt),
                    likeCountMap.getOrDefault(chatroomId, 0L),
                    participantCountMap.getOrDefault(chatroomId, 0L),
                    chatroom.getIsClosed()
            );
        }
    }

    private record SummaryValues(
            LocalDateTime createdAt,
            LocalDateTime lastMessageAt,
            Long likeCount,
            Long participantCount,
            Boolean isClosed
    ) {
    }

    private record ReconcileChunkResult(
            Long lastChatroomId,
            int insertedCount,
            int updatedCount,
            boolean empty
    ) {
        private boolean isEmpty() {
            return empty;
        }

        private static ReconcileChunkResult empty(Long lastChatroomId) {
            return new ReconcileChunkResult(lastChatroomId, 0, 0, true);
        }
    }
}

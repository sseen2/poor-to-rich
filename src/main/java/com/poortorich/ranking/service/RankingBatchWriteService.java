package com.poortorich.ranking.service;

import com.poortorich.chat.entity.ChatMessage;
import com.poortorich.chat.entity.ChatParticipant;
import com.poortorich.chat.entity.enums.ChatroomRole;
import com.poortorich.chat.entity.enums.ChatMessageType;
import com.poortorich.chat.entity.enums.RankingStatus;
import com.poortorich.chat.realtime.event.user.RankingProfileUpdateEvent;
import com.poortorich.chat.response.ChatParticipantProfile;
import com.poortorich.chat.service.ChatMessageService;
import com.poortorich.chat.service.ChatParticipantService;
import com.poortorich.chat.util.mapper.ParticipantProfileMapper;
import com.poortorich.ranking.entity.Ranking;
import com.poortorich.ranking.model.BatchRankingResult;
import com.poortorich.ranking.model.RankingCalculationResult;
import com.poortorich.ranking.payload.response.RankingResponsePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class RankingBatchWriteService {

    private final ChatParticipantService chatParticipantService;
    private final ChatMessageService chatMessageService;
    private final RankingService rankingService;
    private final ParticipantProfileMapper profileMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public List<BatchRankingResult> save(List<Long> chatroomIds, List<RankingCalculationResult> calculations) {
        List<Long> firstRankerIds = getFirstRankerIds(calculations);
        chatParticipantService.resetRankingStatusByChatroomIdsExcludingParticipantIds(chatroomIds, firstRankerIds);

        if (calculations == null || calculations.isEmpty()) {
            return List.of();
        }

        updateFirstRankerStatus(calculations, RankingStatus.SAVER);
        updateFirstRankerStatus(calculations, RankingStatus.FLEXER);
        publishFirstRankerProfileUpdateEvents(calculations);

        List<Ranking> savedRankings = rankingService.saveAll(calculations.stream()
                .map(RankingCalculationResult::ranking)
                .toList());
        Map<Long, RankingCalculationResult> calculationByChatroomId = calculations.stream()
                .collect(Collectors.toMap(
                        calculation -> calculation.chatroom().getId(),
                        calculation -> calculation
                ));

        List<ChatMessage> rankingMessages = chatMessageService.saveRankingMessages(savedRankings);
        Map<Long, ChatMessage> messageByRankingId = rankingMessages.stream()
                .filter(message -> Objects.nonNull(message.getRankingId()))
                .collect(Collectors.toMap(ChatMessage::getRankingId, message -> message));

        return savedRankings.stream()
                .map(ranking -> buildBatchRankingResult(
                        ranking,
                        calculationByChatroomId.get(ranking.getChatroom().getId()),
                        messageByRankingId.get(ranking.getId())
                ))
                .toList();
    }

    private void updateFirstRankerStatus(List<RankingCalculationResult> calculations, RankingStatus status) {
        List<Long> participantIds = calculations.stream()
                .map(calculation -> getFirstRanker(calculation, status))
                .filter(Objects::nonNull)
                .filter(participant -> !status.equals(participant.getRankingStatus()))
                .map(ChatParticipant::getId)
                .filter(Objects::nonNull)
                .toList();

        chatParticipantService.updateRankingStatusByIdsWhereStatusNot(participantIds, status);
    }

    private List<Long> getFirstRankerIds(List<RankingCalculationResult> calculations) {
        if (calculations == null || calculations.isEmpty()) {
            return List.of();
        }

        return calculations.stream()
                .flatMap(calculation -> java.util.stream.Stream.of(
                        getFirstRanker(calculation, RankingStatus.SAVER),
                        getFirstRanker(calculation, RankingStatus.FLEXER)
                ))
                .filter(Objects::nonNull)
                .map(ChatParticipant::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void publishFirstRankerProfileUpdateEvents(List<RankingCalculationResult> calculations) {
        calculations.stream()
                .flatMap(calculation -> java.util.stream.Stream.of(
                        buildRankingProfileUpdateEvent(calculation, RankingStatus.SAVER),
                        buildRankingProfileUpdateEvent(calculation, RankingStatus.FLEXER)
                ))
                .filter(Objects::nonNull)
                .forEach(eventPublisher::publishEvent);
    }

    private RankingProfileUpdateEvent buildRankingProfileUpdateEvent(
            RankingCalculationResult calculation,
            RankingStatus status
    ) {
        ChatParticipant participant = getFirstRanker(calculation, status);
        if (Objects.isNull(participant) || Objects.isNull(participant.getUser())) {
            return null;
        }

        return RankingProfileUpdateEvent.builder()
                .chatroomId(calculation.chatroom().getId())
                .userId(participant.getUser().getId())
                .profileImage(participant.getUser().getProfileImage())
                .nickname(participant.getUser().getNickname())
                .isHost(ChatroomRole.HOST.equals(participant.getRole()))
                .rankingStatus(status)
                .build();
    }

    private ChatParticipant getFirstRanker(RankingCalculationResult calculation, RankingStatus status) {
        List<ChatParticipant> participants = RankingStatus.SAVER.equals(status)
                ? calculation.savers()
                : calculation.flexers();

        if (participants == null || participants.isEmpty()) {
            return null;
        }

        return participants.getFirst();
    }

    private BatchRankingResult buildBatchRankingResult(
            Ranking ranking,
            RankingCalculationResult calculation,
            ChatMessage message
    ) {
        return BatchRankingResult.builder()
                .chatroom(calculation.chatroom())
                .payload(RankingResponsePayload.builder()
                        .messageId(Objects.nonNull(message) ? message.getId() : null)
                        .rankingId(ranking.getId())
                        .chatroomId(calculation.chatroom().getId())
                        .rankedAt(Objects.nonNull(message) && Objects.nonNull(message.getSentAt())
                                ? message.getSentAt().toLocalDate()
                                : LocalDate.now())
                        .sentAt(Objects.nonNull(message) ? message.getSentAt() : LocalDateTime.now())
                        .saverRankings(buildRankerProfiles(calculation.savers(), RankingStatus.SAVER))
                        .flexerRankings(buildRankerProfiles(calculation.flexers(), RankingStatus.FLEXER))
                        .messageType(Objects.nonNull(message) ? message.getMessageType() : null)
                        .type(ChatMessageType.RANKING_MESSAGE)
                        .build())
                .build();
    }

    private List<ChatParticipantProfile> buildRankerProfiles(List<ChatParticipant> participants, RankingStatus firstStatus) {
        return IntStream.range(0, Math.min(3, participants.size()))
                .mapToObj(index -> profileMapper.mapToProfile(
                        participants.get(index),
                        index == 0 ? firstStatus : RankingStatus.NONE
                ))
                .toList();
    }
}

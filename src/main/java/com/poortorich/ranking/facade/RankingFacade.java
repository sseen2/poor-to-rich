package com.poortorich.ranking.facade;

import com.poortorich.chat.entity.ChatParticipant;
import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.entity.enums.RankingStatus;
import com.poortorich.chat.realtime.event.user.UserProfileUpdateEvent;
import com.poortorich.chat.response.ChatParticipantProfile;
import com.poortorich.chat.service.ChatMessageService;
import com.poortorich.chat.service.ChatParticipantService;
import com.poortorich.chat.service.ChatroomService;
import com.poortorich.chat.util.mapper.ParticipantProfileMapper;
import com.poortorich.chat.validator.ChatParticipantValidator;
import com.poortorich.global.date.util.DateConverter;
import com.poortorich.ranking.entity.Ranking;
import com.poortorich.ranking.model.BatchRankingResult;
import com.poortorich.ranking.model.ChatroomUserExpenseAggregate;
import com.poortorich.ranking.model.RankingBatchSource;
import com.poortorich.ranking.model.RankingCalculationResult;
import com.poortorich.ranking.model.Rankers;
import com.poortorich.ranking.payload.response.RankingResponsePayload;
import com.poortorich.ranking.response.AllRankingsResponse;
import com.poortorich.ranking.response.LatestRankingResponse;
import com.poortorich.ranking.response.RankingInfoResponse;
import com.poortorich.ranking.service.RankingBatchReadService;
import com.poortorich.ranking.service.RankingBatchWriteService;
import com.poortorich.ranking.service.RankingService;
import com.poortorich.ranking.util.RankingBuilder;
import com.poortorich.ranking.util.calculator.RankingCalculator;
import com.poortorich.user.entity.User;
import com.poortorich.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class RankingFacade {

    private static final int PAGE_SIZE = 21;

    private final UserService userService;
    private final ChatroomService chatroomService;
    private final ChatParticipantService chatParticipantService;
    private final ChatMessageService chatMessageService;
    private final RankingService rankingService;
    private final RankingBatchReadService rankingBatchReadService;
    private final RankingBatchWriteService rankingBatchWriteService;

    private final ParticipantProfileMapper profileMapper;
    private final RankingCalculator rankingCalculator;
    private final ChatParticipantValidator chatParticipantValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final RankingBuilder rankingBuilder;

    @Transactional
    public RankingResponsePayload calculateRankingTest(Long chatroomId) {
        Chatroom chatroom = chatroomService.findById(chatroomId);
        return calculateRanking(chatroom);
    }

    @Transactional
    public RankingResponsePayload calculateRanking(Chatroom chatroom) {
        Rankers rankers = rankingCalculator.calculate(chatroom);
        Ranking ranking = rankingService.create(chatroom, rankers);

        List<ChatParticipant> participants = chatParticipantService.findAllByChatroom(chatroom);

        ChatParticipant prevSaver = chatParticipantService.findByChatroomAndRankingStatus(
                chatroom,
                RankingStatus.SAVER);

        ChatParticipant prevFLexer = chatParticipantService.findByChatroomAndRankingStatus(
                chatroom,
                RankingStatus.FLEXER);

        chatParticipantService.updateAllRankingStatus(participants, RankingStatus.NONE);
        rankingService.updateRankingStatus(rankers);
        if (!Objects.isNull(prevSaver)) {
            eventPublisher.publishEvent(new UserProfileUpdateEvent(prevSaver.getUser().getUsername()));
        }
        if (!Objects.isNull(prevFLexer)) {
            eventPublisher.publishEvent(new UserProfileUpdateEvent(prevFLexer.getUser().getUsername()));
        }
        return chatMessageService.saveRankingMessage(chatroom, ranking);
    }

    public List<BatchRankingResult> calculateRankings(List<Chatroom> chatrooms) {
        if (chatrooms == null || chatrooms.isEmpty()) {
            return List.of();
        }

        List<Long> chatroomIds = chatrooms.stream()
                .map(Chatroom::getId)
                .toList();
        RankingPeriod period = getLastWeekPeriod();
        RankingBatchSource source = rankingBatchReadService.read(chatroomIds, period.startDate(), period.endDate());

        List<RankingCalculationResult> calculations = chatrooms.stream()
                .map(chatroom -> calculateBatchRanking(
                        chatroom,
                        source.participantsByChatroomId().getOrDefault(chatroom.getId(), List.of()),
                        source.participantByChatroomAndUserId().getOrDefault(chatroom.getId(), Map.of()),
                        source.aggregatesByChatroomId().getOrDefault(chatroom.getId(), List.of())
                ))
                .flatMap(Optional::stream)
                .toList();

        return rankingBatchWriteService.save(chatroomIds, calculations);
    }

    private Optional<RankingCalculationResult> calculateBatchRanking(
            Chatroom chatroom,
            List<ChatParticipant> participants,
            Map<Long, ChatParticipant> participantByUserId,
            List<ChatroomUserExpenseAggregate> aggregates
    ) {
        if (participants.size() < 2 || aggregates.isEmpty()) {
            return Optional.empty();
        }

        List<BatchRanker> rankers = aggregates.stream()
                .filter(aggregate -> aggregate.getExpenseDaysCount() >= 3)
                .map(aggregate -> {
                    ChatParticipant participant = participantByUserId.get(aggregate.getUserId());
                    if (Objects.isNull(participant)) {
                        return null;
                    }
                    return new BatchRanker(participant, aggregate);
                })
                .filter(Objects::nonNull)
                .toList();

        if (rankers.size() < 2) {
            return Optional.empty();
        }

        List<ChatParticipant> savers = rankers.stream()
                .sorted(Comparator.comparing(BatchRanker::score))
                .map(BatchRanker::participant)
                .toList();
        List<ChatParticipant> flexers = rankers.stream()
                .sorted(Comparator.comparing(BatchRanker::score).reversed())
                .map(BatchRanker::participant)
                .toList();

        Ranking ranking = Ranking.builder()
                .chatroom(chatroom)
                .saverFirst(getUserId(savers, 0))
                .saverSecond(getUserId(savers, 1))
                .saverThird(getUserId(savers, 2))
                .flexerFirst(getUserId(flexers, 0))
                .flexerSecond(getUserId(flexers, 1))
                .flexerThird(getUserId(flexers, 2))
                .build();

        return Optional.of(new RankingCalculationResult(chatroom, ranking, savers, flexers));
    }

    private Long getUserId(List<ChatParticipant> participants, int index) {
        if (participants.size() <= index) {
            return null;
        }

        return participants.get(index).getUser().getId();
    }

    private RankingPeriod getLastWeekPeriod() {
        LocalDate today = LocalDate.now();
        LocalDate lastWeekSunday = today.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        LocalDate lastWeekMonday = lastWeekSunday.with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
        return new RankingPeriod(lastWeekMonday, lastWeekSunday);
    }

    @Transactional(readOnly = true)
    public LatestRankingResult getLatestRanking(String username, Long chatroomId) {
        User user = userService.findUserByUsername(username);
        Chatroom chatroom = chatroomService.findById(chatroomId);
        chatParticipantValidator.validateIsParticipate(user, chatroom);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastMonday = now
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate()
                .atStartOfDay();
        Ranking latestRanking = rankingService.findLatestRanking(chatroom, lastMonday, now);

        return getLatestRankingResponse(latestRanking, chatroom, lastMonday);
    }

    private LatestRankingResult getLatestRankingResponse(
            Ranking latestRanking,
            Chatroom chatroom,
            LocalDateTime lastMonday
    ) {
        if (latestRanking == null) {
            LatestRankingResponse response = rankingBuilder.buildNotFoundLatestRankingResponse(lastMonday);
            return LatestRankingResult.notFound(response);
        }

        ChatParticipant saver = chatParticipantService.findByUserIdAndChatroom(
                latestRanking.getSaverFirst(),
                chatroom
        );
        ChatParticipant flexer = chatParticipantService.findByUserIdAndChatroom(
                latestRanking.getFlexerFirst(),
                chatroom
        );

        LatestRankingResponse response = rankingBuilder.buildLatestRankingResponse(latestRanking, saver, flexer);
        return LatestRankingResult.found(response);
    }

    @Transactional(readOnly = true)
    public AllRankingsResponse getAllRankings(String username, Long chatroomId, String cursor) {
        User user = userService.findUserByUsername(username);
        Chatroom chatroom = chatroomService.findById(chatroomId);
        chatParticipantValidator.validateIsParticipate(user, chatroom);

        Map<LocalDate, Ranking> rankings = getMondayRankings(chatroom, cursor);

        boolean hasNext = rankings.size() == PAGE_SIZE;
        LocalDate lastKey = rankings.keySet()
                .stream()
                .reduce((first, second) -> second)
                .orElse(null);

        return buildAllRankingsResponse(
                hasNext,
                hasNext ? lastKey.toString() : null,
                rankings,
                chatroom
        );
    }

    private Map<LocalDate, Ranking> getMondayRankings(Chatroom chatroom, String cursor) {
        List<LocalDate> mondays = getMondays(DateConverter.parseDate(cursor).atStartOfDay(), getFloorMonday(chatroom));
        if (mondays.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<Ranking> rankings = rankingService.findAllRankings(chatroom, mondays);

        Map<LocalDate, Ranking> byDate = rankings.stream()
                .collect(Collectors.toMap(
                        ranking -> ranking.getCreatedDate().toLocalDate(),
                        ranking -> ranking)
                );

        Map<LocalDate, Ranking> result = new LinkedHashMap<>();
        for (LocalDate monday : mondays) {
            result.put(monday, byDate.getOrDefault(monday, null));
        }
        return result;
    }

    private LocalDate getFloorMonday(Chatroom chatroom) {
        return chatroom.getCreatedDate()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .toLocalDate();
    }

    private List<LocalDate> getMondays(LocalDateTime cursor, LocalDate floorMonday) {
        LocalDate recentMonday = cursor
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate();

        List<LocalDate> mondays = IntStream.range(0, PAGE_SIZE)
                .mapToObj(recentMonday::minusWeeks)
                .toList();

        return mondays.stream()
                .filter(m -> !m.isBefore(floorMonday))
                .toList();
    }

    private AllRankingsResponse buildAllRankingsResponse(
            Boolean hasNext,
            String nextCursor,
            Map<LocalDate, Ranking> rankings,
            Chatroom chatroom
    ) {
        if (rankings.isEmpty()) {
            return AllRankingsResponse.builder().build();
        }

        return AllRankingsResponse.builder()
                .hasNext(hasNext)
                .nextCursor(nextCursor)
                .rankings(rankings.entrySet().stream()
                        .limit(hasNext ? (rankings.size() - 1L) : rankings.size())
                        .map(entry -> buildRankingInfoResponse(entry.getKey(), entry.getValue(), chatroom))
                        .toList())
                .build();
    }

    private RankingInfoResponse buildRankingInfoResponse(LocalDate rankingAt, Ranking ranking, Chatroom chatroom) {
        if (ranking == null) {
            return RankingInfoResponse.builder()
                    .rankedAt(rankingAt.toString())
                    .saverRankings(List.of())
                    .flexerRankings(List.of())
                    .build();
        }

        return RankingInfoResponse.builder()
                .rankingId(ranking.getId())
                .rankedAt(rankingAt.toString())
                .saverRankings(buildProfileResponse(
                        Arrays.asList(ranking.getSaverFirst(), ranking.getSaverSecond(), ranking.getSaverThird()),
                        RankingStatus.SAVER,
                        chatroom)
                )
                .flexerRankings(buildProfileResponse(
                        Arrays.asList(ranking.getFlexerFirst(), ranking.getFlexerSecond(), ranking.getFlexerThird()),
                        RankingStatus.FLEXER,
                        chatroom)
                )
                .build();
    }

    private List<ChatParticipantProfile> buildProfileResponse(List<Long> userIds, RankingStatus type, Chatroom chatroom) {
        List<User> users = userService.findAllById(userIds);

        return IntStream.range(0, userIds.size())
                .mapToObj(i -> {
                    User user = users.stream()
                            .filter(p -> Objects.equals(p.getId(), userIds.get(i)))
                            .findFirst()
                            .orElse(null);

                    if (user == null) {
                        return null;
                    }

                    ChatParticipant chatParticipant = chatParticipantService.findByUserAndChatroom(user, chatroom);
                    return profileMapper.mapToProfile(chatParticipant, i == 0 ? type : RankingStatus.NONE);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public record LatestRankingResult(boolean found, LatestRankingResponse response) {
        public static LatestRankingResult found(LatestRankingResponse response) {
            return new LatestRankingResult(true, response);
        }

        public static LatestRankingResult notFound(LatestRankingResponse response) {
            return new LatestRankingResult(false, response);
        }
    }

    private record RankingPeriod(LocalDate startDate, LocalDate endDate) {
    }

    private record BatchRanker(ChatParticipant participant, ChatroomUserExpenseAggregate aggregate) {
        private BigDecimal score() {
            double dayRatio = aggregate.getExpenseDaysCount() / 7.;
            return aggregate.getTotalCostAsBigDecimal().multiply(BigDecimal.valueOf(dayRatio));
        }
    }
}

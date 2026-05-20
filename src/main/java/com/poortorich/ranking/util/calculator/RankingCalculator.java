package com.poortorich.ranking.util.calculator;

import com.poortorich.accountbook.service.AccountBookService;
import com.poortorich.chat.entity.ChatParticipant;
import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.service.ChatParticipantService;
import com.poortorich.ranking.model.Rankers;
import com.poortorich.ranking.model.UserExpenseAggregate;
import com.poortorich.user.entity.User;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RankingCalculator {

    private final ChatParticipantService participantService;
    private final AccountBookService accountBookService;

    // 랭킹 계산 메서드
    public Rankers calculate(Chatroom chatroom) {
        // 채팅방 참여자 목록 조회
        List<ChatParticipant> participants = participantService.findAllByChatroom(chatroom);
        RankingCalculationData rankingCalculationData = getRankingCalculationData(participants);

        // 랭킹 계산 조건에 맞는 참여자가 없는 경우 null 반환
        if (!rankingCalculationData.isCalculate()) {
            return null;
        }

        // 랭킹 계산 조건에 맞는 참여자가 있는 경우 랭커 데이터 반환
        return Rankers.builder()
                .savers(calculateSaver(rankingCalculationData))
                .flexers(calculateFlexer(rankingCalculationData))
                .build();
    }

    // 랭킹 계산을 위해 필요한 정보와 목록을 담은 객체 반환
    private RankingCalculationData getRankingCalculationData(List<ChatParticipant> participants) {
        // 지난주 월요일과 일요일 날짜 추출
        LocalDate today = LocalDate.now();
        LocalDate lastWeekSunday = today.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        LocalDate lastWeekMonday = lastWeekSunday.with(TemporalAdjusters.previous(DayOfWeek.MONDAY));

        List<User> users = participants.stream().map(ChatParticipant::getUser).toList();

        // userId, 지출 합계, 총 지출 날짜의 수를 담은 UserExpenseAggregate 목록을 얻음
        List<UserExpenseAggregate> expenseAggregates = accountBookService
                .getExpenseAggregatesForUsersInRange(users, lastWeekMonday, lastWeekSunday);

        // calculableParticipants 데이터를 만들기 위한 사전 작업
        // 각 참여자의 집계 데이터에 접근하기 쉽도록 userId를 키 값으로 가지는 Map 자료구조로 변환
        Map<Long, UserExpenseAggregate> aggregateGroupByUserId = expenseAggregates.stream()
                .collect(Collectors.toMap(UserExpenseAggregate::getUserId, aggregate -> aggregate));

        // 랭킹 계산의 핵심 객체
        // 총 지출 날짜 수가 3일 이상인 참여자만 calculableParticipants (랭킹 계산 참여자) 목록에 추가
        List<CalculableParticipant> calculableParticipants = participants.stream()
                .filter(participant -> {
                    UserExpenseAggregate aggregate = aggregateGroupByUserId.get(participant.getUser().getId());
                    return aggregate != null && aggregate.getExpenseDaysCount() >= 3;
                })
                .map(participant -> {
                    UserExpenseAggregate aggregate = aggregateGroupByUserId.get(participant.getUser().getId());
                    return CalculableParticipant.builder()
                            .expenseDays(aggregate.getExpenseDaysCount())
                            .participant(participant)
                            .totalExpenseCost(aggregate.getTotalCostAsBigDecimal())
                            .build();
                })
                .toList();

        return RankingCalculationData.builder()
                .startDate(lastWeekMonday)
                .endDate(lastWeekSunday)
                .participants(calculableParticipants)
                .build();
    }

    // 절약왕 계산 메서드, 참여자 목록 반환
    private List<ChatParticipant> calculateSaver(RankingCalculationData calculationData) {
        List<CalculableParticipant> participants = calculationData.getParticipants();
        List<RankingResult> rankingResults = participants.stream()
                .map(participant -> RankingResult.builder()
                        .score(getScore(participant))
                        .participant(participant.getParticipant())
                        .build())
                .sorted()
                .toList();

        return rankingResults.stream()
                .map(RankingResult::getParticipant)
                .toList();
    }

    // 지출왕 계산 메서드, 참여자 목록 반환
    private List<ChatParticipant> calculateFlexer(RankingCalculationData calculationData) {
        List<CalculableParticipant> participants = calculationData.getParticipants();
        List<RankingResult> rankingResults = participants.stream()
                .map(participant -> RankingResult.builder()
                        .score(getScore(participant))
                        .participant(participant.getParticipant())
                        .build())
                .sorted(Comparator.reverseOrder())
                .toList();

        return rankingResults.stream()
                .map(RankingResult::getParticipant)
                .toList();
    }

    // 참여자 지출 점수 계산 메서드
    private BigDecimal getScore(CalculableParticipant participant) {
        BigDecimal totalCost = participant.getTotalExpenseCost();
        double dayRatio = participant.getExpenseDays() / 7.;
        return totalCost.multiply(BigDecimal.valueOf(dayRatio));
    }

    @Getter
    @Builder
    protected static class CalculableParticipant {
        private int expenseDays;
        private ChatParticipant participant;
        private BigDecimal totalExpenseCost;
    }

    @Getter
    @Builder
    protected static class RankingCalculationData {
        private LocalDate startDate;
        private LocalDate endDate;
        private List<CalculableParticipant> participants;

        public boolean isCalculate() {
            return !participants.isEmpty() && participants.size() >= 2;
        }
    }

    @Getter
    @Builder
    protected static class RankingResult implements Comparable<RankingResult> {
        private BigDecimal score;
        private ChatParticipant participant;

        @Override
        public int compareTo(RankingResult other) {
            if (this.score == null && other.score == null) {
                return 0;
            }
            if (this.score == null) {
                return 1;
            }
            if (other.score == null) {
                return -1;
            }

            return this.score.compareTo(other.score);
        }
    }
}

package com.poortorich.stats.service;

import com.poortorich.accountbook.enums.AccountBookType;
import com.poortorich.accountbook.model.domain.PeriodAmount;
import com.poortorich.category.entity.Category;
import com.poortorich.category.entity.enums.CategoryType;
import com.poortorich.global.date.util.DateConverter;
import com.poortorich.global.event.MonthlySummaryEvent;
import com.poortorich.stats.entity.MonthlySummary;
import com.poortorich.stats.repository.MonthlySummaryRepository;
import com.poortorich.user.entity.User;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MonthlySummaryService {

    private final MonthlySummaryRepository summaryRepository;

    @Transactional
    public MonthlySummary getOrCreateSummary(MonthlySummaryEvent event) {
        String period = DateConverter.formatToYearMonth(event.date());

        return summaryRepository.findByUserAndCategoryAndPeriodWithLock(event.user(), event.category(), period)
                .orElseGet(() -> createSummary(event, period));
    }

    private MonthlySummary createSummary(MonthlySummaryEvent event, String period) {
        MonthlySummary summary = MonthlySummary.builder()
                .user(event.user())
                .period(period)
                .category(event.category())
                .type(getAccountBookTypeByCategoryType(event.category().getType()))
                .totalAmount(0L)
                .build();

        return summaryRepository.save(summary);
    }

    private AccountBookType getAccountBookTypeByCategoryType(CategoryType type) {
        if (type.equals(CategoryType.DEFAULT_EXPENSE) || type.equals(CategoryType.CUSTOM_EXPENSE)) {
            return AccountBookType.EXPENSE;
        }

        return AccountBookType.INCOME;
    }

    @Transactional(readOnly = true)
    public List<PeriodAmount> getPeriodAmounts(
            User user,
            Category category,
            LocalDate startDate,
            LocalDate endDate
    ) {
        String startPeriod = DateConverter.formatToYearMonth(startDate);
        String endPeriod = DateConverter.formatToYearMonth(endDate);

        List<PeriodAmount> results = summaryRepository.getPeriodAmounts(user, category, startPeriod, endPeriod);

        return fillMissingPeriods(results, startDate, endDate);
    }

    private List<PeriodAmount> fillMissingPeriods(List<PeriodAmount> results, LocalDate startDate, LocalDate endDate) {
        Map<String, Long> periodAmountMap = results.stream()
                .collect(Collectors.toMap(PeriodAmount::getPeriod, PeriodAmount::getTotalAmount));

        YearMonth start = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);

        return Stream.iterate(start, date -> !date.isAfter(end), date -> date.plusMonths(1))
                .map(date -> {
                    String periodKey = DateConverter.formatToYearMonth(date);
                    String displayPeriod = date.getMonthValue() + "월";

                    return new PeriodAmount(
                            displayPeriod,
                            periodAmountMap.getOrDefault(periodKey, 0L)
                    );
                })
                .collect(Collectors.toList());
    }
}

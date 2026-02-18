package com.poortorich.accountbook.model.domain;

import com.poortorich.global.date.constants.DatePattern;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PeriodAmount {

    private String period;
    private Long totalAmount;

    public PeriodAmount(Object period, Object totalAmount) {
        this.period = convertPeriodToString(period);
        this.totalAmount = (Long) totalAmount;
    }

    private String convertPeriodToString(Object period) {
        String date = (String) period;
        YearMonth yearMonth = YearMonth.parse(date, DateTimeFormatter.ofPattern(DatePattern.YEAR_MONTH_PATTERN));

        return yearMonth.getMonthValue() +"월";
    }
}

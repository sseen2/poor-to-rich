package com.poortorich.accountbook.model.domain;

import java.time.LocalDate;

public record DailyAmount(
    LocalDate date,
    Long amount
) {
}

package com.poortorich.global.event;

import com.poortorich.category.entity.Category;
import com.poortorich.user.entity.User;
import java.time.LocalDate;

public record MonthlySummaryEvent(
    User user,
    Long amount,
    LocalDate date,
    Category category
) {
}

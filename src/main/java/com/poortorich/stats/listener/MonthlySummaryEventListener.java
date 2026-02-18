package com.poortorich.stats.listener;

import com.poortorich.global.event.MonthlySummaryEvent;
import com.poortorich.stats.entity.MonthlySummary;
import com.poortorich.stats.service.MonthlySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MonthlySummaryEventListener {

    private final MonthlySummaryService summaryService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleMonthlySummaryEvent(MonthlySummaryEvent event) {
        MonthlySummary summary = summaryService.getOrCreateSummary(event);

        summary.modifyAmount(event.amount());
    }
}

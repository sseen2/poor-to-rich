package com.poortorich.ranking.controller;

import com.poortorich.global.response.BaseResponse;
import com.poortorich.ranking.facade.RankingTestFacade;
import com.poortorich.ranking.response.enums.RankingResponse;
import com.poortorich.ranking.schedules.RankingScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ranking/test")
public class RankingTestController {

    private final RankingScheduler rankingScheduler;
    private final RankingTestFacade rankingTestFacade;

    @PostMapping("/trigger")
    public ResponseEntity<BaseResponse> triggerRanking() {
        rankingScheduler.calculateAndBroadcastWeeklyRanking();
        return BaseResponse.toResponseEntity(RankingResponse.RANKING_TRIGGER_SUCCESS);
    }

    @PostMapping("/trigger/batch")
    public ResponseEntity<BaseResponse> triggerRankingBatch() {
        rankingScheduler.calculateAndBroadcastWeeklyRankingBatch();
        return BaseResponse.toResponseEntity(RankingResponse.RANKING_TRIGGER_SUCCESS);
    }

    @DeleteMapping("/data")
    public ResponseEntity<BaseResponse> deleteTestData(@RequestParam String since) {
        LocalDateTime sinceDateTime = Instant.parse(since)
                .atZone(ZoneId.of("Asia/Seoul"))
                .toLocalDateTime();
        rankingTestFacade.deleteTestData(sinceDateTime);
        return BaseResponse.toResponseEntity(RankingResponse.RANKING_TEST_DATA_CLEANUP_SUCCESS);
    }
}

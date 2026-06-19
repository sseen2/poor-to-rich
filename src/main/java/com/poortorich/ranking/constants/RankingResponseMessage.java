package com.poortorich.ranking.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RankingResponseMessage {

    public static final String GET_LATEST_RANKING_SUCCESS = "최신 랭킹 조회를 완료했습니다.";
    public static final String GET_LATEST_RANKING_NOT_FOUND = "랭킹이 집계되지 않았습니다.";
    public static final String GET_ALL_RANKINGS_SUCCESS = "랭킹 목록 조회를 완료했습니다.";
    public static final String RANKING_TRIGGER_SUCCESS = "랭킹 스케줄러를 실행했습니다.";
    public static final String RANKING_SCHEDULER_ALREADY_RUNNING = "랭킹 스케줄러가 이미 실행 중입니다.";
    public static final String RANKING_TEST_DATA_CLEANUP_SUCCESS = "테스트 데이터가 정리되었습니다.";
}

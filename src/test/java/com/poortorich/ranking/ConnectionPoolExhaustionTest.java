package com.poortorich.ranking;

import com.poortorich.ranking.facade.RankingTestFacade;
import com.poortorich.ranking.schedules.RankingScheduler;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectionPoolExhaustionTest {

    @Autowired
    private RankingScheduler rankingScheduler;

    @Autowired
    private RankingTestFacade rankingTestFacade;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    @Qualifier("rankingTaskExecutor")
    private ThreadPoolTaskExecutor rankingTaskExecutor;

    @LocalServerPort
    private int port;

    private LocalDateTime testStartTime;

    @BeforeEach
    void setUp() {
        testStartTime = LocalDateTime.now();
    }

    @AfterEach
    void tearDown() {
        rankingTestFacade.deleteTestData(testStartTime);
    }

    /**
     * 스케줄러 실행 중 API 동시 호출 실패 검증
     *
     * 검증 목적:
     * - 랭킹 스케줄러가 커넥션 풀을 점유하는 동안 다른 API 요청이 실패하는지 확인
     * - DB를 조회하는 API는 실패하고, DB를 사용하지 않는 API는 성공해야 커넥션 풀 고갈이 원인임을 증명
     *
     * 호출 API 종류:
     * - POST /user/exists/username : DB 조회 (user 테이블)
     * - POST /user/exists/nickname : DB 조회 (user 테이블)
     * - GET  /auth/health          : DB 미사용 (비교 기준 — 항상 성공해야 정상)
     */
    @Test
    void 스케줄러_실행_중_API_동시_호출_실패_검증() throws InterruptedException {
        HikariDataSource hikariDS = (HikariDataSource) dataSource;
        String baseUrl = "http://localhost:" + port;

        int totalRequests = 60; // API당 20건씩 × 3종류
        AtomicInteger usernameCheckSuccess = new AtomicInteger(0);
        AtomicInteger usernameCheckFail = new AtomicInteger(0);
        AtomicInteger nicknameCheckSuccess = new AtomicInteger(0);
        AtomicInteger nicknameCheckFail = new AtomicInteger(0);
        AtomicInteger healthCheckSuccess = new AtomicInteger(0);
        AtomicInteger healthCheckFail = new AtomicInteger(0);

        // 1. 스케줄러 시작 (CompletableFuture 제출 후 즉시 반환)
        long schedulerStart = System.currentTimeMillis();
        rankingScheduler.calculateAndBroadcastWeeklyRanking();

        // 배치 스레드들이 커넥션을 점유하기 시작할 때까지 대기
        Thread.sleep(500);

        // 2. 여러 API를 동시에 호출
        List<Thread> apiThreads = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            final int idx = i;
            apiThreads.add(new Thread(() -> {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    ResponseEntity<String> response;

                    if (idx % 3 == 0) {
                        // API 1: 유저명 존재 확인 (DB 조회)
                        String body = String.format("{\"username\":\"user%d\"}", (idx + 1));
                        response = restTemplate.exchange(
                                baseUrl + "/user/exists/username",
                                HttpMethod.POST,
                                new HttpEntity<>(body, headers),
                                String.class
                        );
                        if (response.getStatusCode().is2xxSuccessful()) {
                            usernameCheckSuccess.incrementAndGet();
                        } else {
                            usernameCheckFail.incrementAndGet();
                            String responseBody = response.getBody();
                            System.out.printf("[username 실패] idx=%d, 상태코드=%s, 원인=%s%n", idx, response.getStatusCode(), responseBody);

                            // 에러 바디에 해당 예외 이름이 포함되어 있는지 검증
                            assertTrue(responseBody != null && responseBody.contains("SQLTransientConnectionException"),
                                    "커넥션 타임아웃 예외가 발생해야 합니다.");
                        }

                    } else if (idx % 3 == 1) {
                        // API 2: 닉네임 존재 확인 (DB 조회)
                        String body = String.format("{\"nickname\":\"nick%d\"}", (idx + 1));
                        response = restTemplate.exchange(
                                baseUrl + "/user/exists/nickname",
                                HttpMethod.POST,
                                new HttpEntity<>(body, headers),
                                String.class
                        );
                        if (response.getStatusCode().is2xxSuccessful()) {
                            nicknameCheckSuccess.incrementAndGet();
                        } else {
                            nicknameCheckFail.incrementAndGet();
                            String responseBody = response.getBody();
                            System.out.printf("[nickname 실패] idx=%d, 상태코드=%s, 원인=%s%n", idx, response.getStatusCode(), responseBody);

                            // 에러 바디에 해당 예외 이름이 포함되어 있는지 검증
                            assertTrue(responseBody != null && responseBody.contains("SQLTransientConnectionException"),
                                    "커넥션 타임아웃 예외가 발생해야 합니다.");
                        }

                    } else {
                        // API 3: 헬스체크 (DB 미사용 — 비교 기준)
                        response = restTemplate.exchange(
                                baseUrl + "/auth/health",
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                String.class
                        );
                        if (response.getStatusCode().is2xxSuccessful()) {
                            healthCheckSuccess.incrementAndGet();
                        } else {
                            healthCheckFail.incrementAndGet();
                            System.out.printf("[health 실패] idx=%d, 상태코드=%s%n", idx, response.getStatusCode());

                        }
                    }
                } catch (Exception e) {
                    int apiType = idx % 3;
                    if (apiType == 0) usernameCheckFail.incrementAndGet();
                    else if (apiType == 1) nicknameCheckFail.incrementAndGet();
                    else healthCheckFail.incrementAndGet();
                    System.out.printf("[API 예외] idx=%d, %s: %s%n", idx, e.getClass().getSimpleName(), e.getMessage());
                }
            }));
        }

        apiThreads.forEach(Thread::start);
        for (Thread t : apiThreads) {
            t.join(30_000);
        }

        // 3. 스케줄러 완료 감지 (executor 활성 스레드 + 큐가 모두 비워질 때까지)
        long schedulerEnd = schedulerStart;
        for (int i = 0; i < 480; i++) {
            Thread.sleep(500);
            boolean allDone = rankingTaskExecutor.getActiveCount() == 0
                    && rankingTaskExecutor.getQueueSize() == 0;
            if (allDone) {
                schedulerEnd = System.currentTimeMillis();
                break;
            }
        }

        // 4. 결과 출력
        int totalDbFail = usernameCheckFail.get() + nicknameCheckFail.get();
        int totalDbSuccess = usernameCheckSuccess.get() + nicknameCheckSuccess.get();
        int totalDbRequests = totalRequests / 3 * 2;

        System.out.println();
        System.out.println("============================================");
        System.out.println("            스케줄러 실행 중 API 결과          ");
        System.out.println("============================================");
        System.out.printf("스케줄러 처리 시간:          %dms%n", schedulerEnd - schedulerStart);
        System.out.printf("커넥션 풀 크기:              %d개%n", hikariDS.getMaximumPoolSize());
        System.out.println("--------------------------------------------");
        System.out.printf("[DB 조회 API] username 확인: 성공 %d / 실패 %d%n",
                usernameCheckSuccess.get(), usernameCheckFail.get());
        System.out.printf("[DB 조회 API] nickname 확인: 성공 %d / 실패 %d%n",
                nicknameCheckSuccess.get(), nicknameCheckFail.get());
        System.out.printf("[비교 기준   ] health 확인:  성공 %d / 실패 %d%n",
                healthCheckSuccess.get(), healthCheckFail.get());
        System.out.println("--------------------------------------------");
        System.out.printf("DB 조회 API 전체: 요청 %d건 / 성공 %d건 / 실패 %d건 / 실패율 %.1f%%%n",
                totalDbRequests, totalDbSuccess, totalDbFail,
                totalDbFail * 100.0 / totalDbRequests);
        System.out.println("============================================");

        assertTrue(totalDbFail > 0,
                "DB 조회 API 실패가 발생하지 않았습니다. 커넥션 풀 고갈이 재현되지 않았습니다.\n"
                        + "풀 크기(" + hikariDS.getMaximumPoolSize() + ")를 줄이거나 채팅방 수를 늘려보세요.");
    }
}

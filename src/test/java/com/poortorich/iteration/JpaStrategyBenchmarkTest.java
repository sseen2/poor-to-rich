package com.poortorich.iteration;

import com.poortorich.iteration.entity.Iteration;
import com.poortorich.iteration.entity.enums.EndType;
import com.poortorich.iteration.entity.enums.MonthlyMode;
import com.poortorich.iteration.entity.info.DailyIterationRule;
import com.poortorich.iteration.entity.info.IterationInfo;
import com.poortorich.iteration.entity.info.MonthlyIterationRule;
import com.poortorich.iteration.repository.IterationInfoRepository;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

@SpringBootTest
@ActiveProfiles("test")
@Transactional // 테스트 실행 후 롤백
public class JpaStrategyBenchmarkTest {

    @Autowired
    private IterationInfoRepository iterationInfoRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("상속 전략 (SINGLE vs JOINED) 단건 데이터 성능 측정")
    void benchmarkInheritanceStrategySingle() {
        IterationInfo dailyDummyData = DailyIterationRule.builder()
                .cycle(1)
                .endType(EndType.NEVER)
                .build();

        IterationInfo monthlyDummyData = MonthlyIterationRule.builder()
                .cycle(1)
                .endType(EndType.NEVER)
                .monthlyMode(MonthlyMode.DAY)
                .monthlyDay(25)
                .build();

        StopWatch stopWatch = new StopWatch("상속 전략 벤치마크 단건 데이터 성능 측정");

        // [1 - 1] INSERT 성능 측정 - DAILY
        stopWatch.start("DAILY 타입 단건 쓰기");
        iterationInfoRepository.save(dailyDummyData);
        em.flush();
        em.clear();
        stopWatch.stop();

//        // [1 - 2] INSERT 성능 측정 - MONTHLY
//        stopWatch.start("MONTHLY 타입 단건 쓰기");
//        iterationInfoRepository.save(monthlyDummyData);
//        em.flush();
//        em.clear();
//        stopWatch.stop();

        // [2] 결과 출력
        System.out.println("=========================================");
        System.out.println(stopWatch.prettyPrint());
        System.out.println("=========================================");
    }

    @Test
    @DisplayName("상속 전략 (SINGLE vs JOINED) 대량 데이터 성능 측정")
    void benchmarkInheritanceStrategy() {
        int DATA_SIZE = 10000;
        List<IterationInfo> dummyData = getDummyData(DATA_SIZE);

        StopWatch stopWatch = new StopWatch("상속 전략 벤치마크 (" + DATA_SIZE + "건)");

        // [1] INSERT (쓰기) 성능 측정
        stopWatch.start("1. 대량 쓰기 (INSERT)");
        iterationInfoRepository.saveAll(dummyData);
        em.flush();
        em.clear();
//        bulkInsert(dummyData);              // SINGLE_TABLE bulk insert
//        bulkInsertForJoined(dummyData);     // JOINED bulk insert
        stopWatch.stop();

        // [2] SELECT (다건 조회) 성능 측정
        stopWatch.start("2. 다건 목록 조회 (SELECT)");
        List<IterationInfo> result = iterationInfoRepository.findAll();
        stopWatch.stop();

        // [3] 결과 출력
        System.out.println("=========================================");
        System.out.println("조회된 데이터 수: " + result.size() + "건");
        System.out.println(stopWatch.prettyPrint());
        System.out.println("=========================================");
    }

    @Test
    @DisplayName("JPA saveAll vs JdbcTemplate Batch Insert 성능 비교")
    void batchInsertBenchmark() {
        int DATA_SIZE = 10000;
        List<IterationInfo> dummyData = getDummyData(DATA_SIZE);

        StopWatch stopWatch = new StopWatch("Batch Insert 벤치마크 (1만건)");

        // [1] 기존 방식: JPA saveAll()
        stopWatch.start("1. JPA saveAll() (단건 INSERT 반복)");
        iterationInfoRepository.saveAll(dummyData);
        em.flush();
        em.clear();
        stopWatch.stop();

        // [2] 새로운 방식: JdbcTemplate Batch Insert
        stopWatch.start("2. JdbcTemplate Batch Insert (다중 INSERT)");

        bulkInsert(dummyData);

        stopWatch.stop();

        System.out.println(stopWatch.prettyPrint());
    }

    private List<IterationInfo> getDummyData(int dataSize) {
        List<IterationInfo> dummyData = new ArrayList<>();

        for (int i = 0; i < dataSize; i++) {
            if (i % 2 == 0) {
                dummyData.add(DailyIterationRule.builder()
                        .cycle(1)
                        .endType(EndType.NEVER)
                        .build());
            } else {
                dummyData.add(MonthlyIterationRule.builder()
                        .cycle(1)
                        .endType(EndType.NEVER)
                        .monthlyMode(MonthlyMode.DAY)
                        .monthlyDay(25)
                        .build());
            }
        }

        return dummyData;
    }

    private void bulkInsert(List<IterationInfo> dummyData) {
        String sql = "INSERT INTO iteration_info (iteration_type, cycle, end_type, monthly_mode, monthly_day) " +
                "VALUES (?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                IterationInfo info = dummyData.get(i);

                ps.setInt(2, info.getCycle());
                ps.setString(3, info.getEndType().name());

                if (info instanceof DailyIterationRule) {
                    ps.setString(1, "DAILY");
                    ps.setString(4, null);
                    ps.setObject(5, null);
                } else if (info instanceof MonthlyIterationRule monthly) {
                    ps.setString(1, "MONTHLY");
                    ps.setString(4, monthly.getMonthlyMode().name());
                    ps.setInt(5, monthly.getMonthlyDay());
                }
            }

            @Override
            public int getBatchSize() {
                return dummyData.size();
            }
        });
    }

    private void bulkInsertForJoined(List<IterationInfo> dummyData) {
        // STEP 1. 부모 테이블(iteration_info)에 먼저 INSERT 하고 생성된 ID 가져오기
        String parentSql = "INSERT INTO iteration_info (iteration_type, cycle, end_type) VALUES (?, ?, ?)";

        // 일반 batchUpdate로는 자동 생성된 키를 리턴받기 어려워 execute를 사용해야 합니다.
        List<Long> generatedIds = jdbcTemplate.execute(
                (Connection con) -> con.prepareStatement(parentSql, Statement.RETURN_GENERATED_KEYS),
                ps -> {
                    for (IterationInfo info : dummyData) {
                        if (info instanceof DailyIterationRule) ps.setString(1, "DAILY");
                        else if (info instanceof MonthlyIterationRule) ps.setString(1, "MONTHLY");

                        ps.setInt(2, info.getCycle());
                        ps.setString(3, info.getEndType().name());
                        ps.addBatch();
                    }
                    ps.executeBatch();

                    // DB가 자동 생성해준 ID(PK) 값들을 순서대로 뽑아옵니다.
                    ResultSet rs = ps.getGeneratedKeys();
                    List<Long> ids = new ArrayList<>();
                    while (rs.next()) {
                        ids.add(rs.getLong(1));
                    }
                    return ids;
                }
        );

        // 2. 뽑아온 부모 ID를 원본 객체들과 매핑 & 자식 타입별로 리스트 분리

        List<MonthlyIterationRule> monthlyList = new ArrayList<>();
        List<DailyIterationRule> dailyList = new ArrayList<>();

        for (int i = 0; i < dummyData.size(); i++) {
            IterationInfo info = dummyData.get(i);
            Long generatedId = generatedIds.get(i);

            if (info instanceof MonthlyIterationRule monthly) {
                monthly.setId(generatedId); // 강제로 ID 세팅 (엔티티에 @Setter가 필요해짐)
                monthlyList.add(monthly);
            } else if (info instanceof DailyIterationRule daily) {
                daily.setId(generatedId);
                dailyList.add(daily);
            }
        }

        // 3. 분리된 자식 테이블들에 각각 2차 벌크 인서트 날리기

        // 3-1. Monthly 자식 테이블 INSERT
        if (!monthlyList.isEmpty()) {
            String monthlySql = "INSERT INTO monthly_iteration_rule (id, monthly_mode, monthly_day) VALUES (?, ?, ?)";
            jdbcTemplate.batchUpdate(monthlySql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                    MonthlyIterationRule monthly = monthlyList.get(i);
                    ps.setLong(1, monthly.getId()); // 부모에게 받아온 그 ID!
                    ps.setString(2, monthly.getMonthlyMode().name());
                    ps.setInt(3, monthly.getMonthlyDay());
                }
                @Override
                public int getBatchSize() { return monthlyList.size(); }
            });
        }

        // 3-2. Daily 자식 테이블 INSERT (필드가 없어도 부모랑 연결하기 위해 PK는 넣어야 함)
        if (!dailyList.isEmpty()) {
            String dailySql = "INSERT INTO daily_iteration_rule (id) VALUES (?)";
            jdbcTemplate.batchUpdate(dailySql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                    ps.setLong(1, dailyList.get(i).getId());
                }
                @Override
                public int getBatchSize() { return dailyList.size(); }
            });
        }
    }
}

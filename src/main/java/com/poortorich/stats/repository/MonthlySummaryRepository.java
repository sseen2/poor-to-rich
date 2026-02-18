package com.poortorich.stats.repository;

import com.poortorich.accountbook.model.domain.PeriodAmount;
import com.poortorich.category.entity.Category;
import com.poortorich.stats.entity.MonthlySummary;
import com.poortorich.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MonthlySummaryRepository extends JpaRepository<MonthlySummary, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT ms
          FROM MonthlySummary ms
         WHERE ms.user = :user
           AND ms.category = :category
           AND ms.period = :period
    """)
    Optional<MonthlySummary> findByUserAndCategoryAndPeriodWithLock(
            @Param("user") User user,
            @Param("category") Category category,
            @Param("period") String period
    );

    @Query("""
        SELECT new com.poortorich.accountbook.model.domain.PeriodAmount(
                    ms.period,
                    COALESCE(ms.totalAmount, 0L)
               )
          FROM MonthlySummary ms
         WHERE ms.user = :user
           AND ms.category = :category
           AND ms.period BETWEEN :startPeriod AND :endPeriod
         ORDER BY ms.period ASC
    """)
    List<PeriodAmount> getPeriodAmounts(
            @Param("user") User user,
            @Param("category") Category category,
            @Param("startPeriod") String startPeriod,
            @Param("endPeriod") String endPeriod
    );
}

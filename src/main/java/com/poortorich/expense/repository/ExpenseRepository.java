package com.poortorich.expense.repository;

import com.poortorich.accountbook.model.domain.DailyAmount;
import com.poortorich.accountbook.model.domain.PeriodAmount;
import com.poortorich.category.entity.Category;
import com.poortorich.expense.entity.Expense;
import com.poortorich.ranking.model.UserExpenseAggregate;
import com.poortorich.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByUserAndId(User user, Long id);

    @Query("""
        SELECT e
          FROM Expense e
          LEFT JOIN FETCH e.generatedIterationExpenses gie
          JOIN FETCH e.user u
         WHERE e.user = :user
           AND e.expenseDate BETWEEN :startDate AND :endDate
    """)
    List<Expense> findByUserAndExpenseDateBetween(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT e
          FROM Expense e
          LEFT JOIN FETCH e.generatedIterationExpenses gie
          JOIN FETCH e.category c
          JOIN FETCH e.user u
         WHERE e.user = :user
           AND e.category = :category
           AND e.expenseDate BETWEEN :startDate AND :endDate
    """)
    List<Expense> findByUserAndCategoryAndExpenseDateBetween(
            @Param("user") User user,
            @Param("category") Category category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<Expense> findByUserAndCategoryAndExpenseDate(User user, Category category, LocalDate expenseDate);

    @Query("""
            SELECT e
            FROM Expense e
            WHERE e.category = :category
            AND e.user = :user
            AND e.expenseDate >= :startDate
            AND e.expenseDate <= :endDate
            AND e.expenseDate >= :cursor
            ORDER BY e.expenseDate ASC
            """
    )
    Slice<Expense> findExpenseByUserAndCategoryWithinDateRangeWithCursorAsc(
            @Param("user") User user,
            @Param("category") Category category,
            @Param("startDate") LocalDate startDate,
            @Param("cursor") LocalDate cursor,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    @Query("""
            SELECT e
            FROM Expense e
            WHERE e.category = :category
            AND e.user = :user
            AND e.expenseDate >= :startDate
            AND e.expenseDate <= :endDate
            AND e.expenseDate <= :cursor
            ORDER BY e.expenseDate DESC
            """)
    Slice<Expense> findExpenseByUserAndCategoryWithinDateRangeWithCursorDesc(
            @Param("user") User user,
            @Param("category") Category category,
            @Param("startDate") LocalDate startDate,
            @Param("cursor") LocalDate cursor,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    @Query("""
            SELECT e
            FROM Expense e
            WHERE e.user = :user
              AND e.expenseDate >= :startDate
              AND e.expenseDate <= :endDate
              AND e.expenseDate >= :cursor
            ORDER BY e.expenseDate ASC
            """)
    Slice<Expense> findExpenseByUserWithinDateRangeWithCursorAsc(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("cursor") LocalDate cursor,
            Pageable pageable
    );

    @Query("""
            SELECT e
            FROM Expense e
            WHERE e.user = :user
              AND e.expenseDate >= :startDate
              AND e.expenseDate <= :endDate
              AND e.expenseDate <= :cursor
            ORDER BY e.expenseDate DESC
            """)
    Slice<Expense> findExpenseByUserWithinDateRangeWithCursorDesc(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("cursor") LocalDate cursor,
            Pageable pageable
    );

    List<Expense> findByUserAndExpenseDate(User user, LocalDate expenseDate);

    @Query("""
            SELECT COUNT(e)
            FROM Expense e
            WHERE e.category = :category
            AND e.user = :user
            AND e.expenseDate >= :startDate
            AND e.expenseDate <= :endDate
            """)
    Long countByUserAndCategoryBetweenDates(
            @Param("user") User user,
            @Param("category") Category category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT COUNT(e)
            FROM Expense e
            WHERE e.user = :user
              AND e.expenseDate >= :startDate
              AND e.expenseDate <= :endDate
            """)
    Long countByUserAndBetweenDates(User user, LocalDate startDate, LocalDate endDate);

    void deleteByUser(User user);

    List<Expense> findByUserAndCategory(User user, Category category);

    @Query("""
            SELECT NEW  com.poortorich.ranking.model.UserExpenseAggregate(
                e.user.id,
                COALESCE(SUM(e.cost), 0L),
                COUNT(DISTINCT e.expenseDate)
            )
            FROM Expense e
            WHERE e.user IN :users
            AND e.expenseDate BETWEEN :startDate AND :endDate
            GROUP BY e.user.id
            """)
    List<UserExpenseAggregate> findExpenseAggregatesByUsersAndDateRange(
            @Param("users") List<User> users,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT COALESCE(SUM(e.cost), 0L)
          FROM Expense e
         WHERE e.user = :user
           AND e.expenseDate BETWEEN :startDate AND :endDate
    """)
    Long sumAmountByDateBetween(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT new com.poortorich.accountbook.model.domain.DailyAmount(
                   e.expenseDate,
                   COALESCE(SUM(e.cost), 0L)
               )
          FROM Expense e
         WHERE e.user = :user
           AND e.expenseDate BETWEEN :startDate AND :endDate
         GROUP BY e.expenseDate
         ORDER BY e.expenseDate
    """)
    List<DailyAmount> sumDailyAmounts(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT COALESCE(SUM(e.cost), 0L)
          FROM Expense e
         WHERE e.user = :user
           AND e.category = :category
           AND e.expenseDate BETWEEN :startDate AND :endDate
    """)
    Long sumExpenseAmountByDateAndCategory(
            @Param("user") User user,
            @Param("category") Category category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT new com.poortorich.accountbook.model.domain.PeriodAmount(
                    FUNCTION('DATE_FORMAT', e.expenseDate, '%Y-%m'),
                    COALESCE(SUM(e.cost), 0L)
               )
          FROM Expense e
         WHERE e.user = :user
           AND e.category = :category
           AND e.expenseDate BETWEEN :startDate AND :endDate
         GROUP BY FUNCTION('DATE_FORMAT', e.expenseDate, '%Y-%m')
         ORDER BY FUNCTION('DATE_FORMAT', e.expenseDate, '%Y-%m') ASC
    """)
    List<PeriodAmount> sumPeriodExpenseAmountsByCategory(
            @Param("user") User user,
            @Param("category") Category category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}

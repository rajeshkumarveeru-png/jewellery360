package com.jewellery360.repository;

import com.jewellery360.domain.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    @EntityGraph(attributePaths = {"branch", "createdBy"})
    @Query("select e from Expense e where e.company.id = :companyId and (:branchId is null or e.branch.id = :branchId) and e.expenseDate between :from and :to order by e.expenseDate desc, e.id desc")
    List<Expense> between(Long companyId, Long branchId, LocalDate from, LocalDate to);

    @Query("select distinct e.category from Expense e where e.company.id = :companyId order by e.category")
    List<String> categories(Long companyId);
}

package com.jewellery360.repository;

import com.jewellery360.domain.GoldRate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GoldRateRepository extends JpaRepository<GoldRate, Long> {
    Optional<GoldRate> findByCompanyIdAndBranchIdAndPurityIdAndRateDate(Long companyId, Long branchId, Long purityId, LocalDate rateDate);

    /** Latest rate on or before the day, per purity, for one branch. */
    @EntityGraph(attributePaths = {"purity", "updatedBy"})
    @Query("""
            select r from GoldRate r where r.branch.id = :branchId and r.rateDate = (
              select max(x.rateDate) from GoldRate x where x.branch.id = :branchId and x.purity.id = r.purity.id and x.rateDate <= :day)
            """)
    List<GoldRate> latestForBranch(Long branchId, LocalDate day);

    @EntityGraph(attributePaths = {"purity", "updatedBy"})
    List<GoldRate> findByBranchIdAndRateDateBetweenOrderByRateDateDesc(Long branchId, LocalDate from, LocalDate to);
}

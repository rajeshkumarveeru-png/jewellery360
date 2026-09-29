package com.jewellery360.repository;

import com.jewellery360.domain.GoldRate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GoldRateRepository extends JpaRepository<GoldRate, Long> {
    @EntityGraph(attributePaths = {"company", "branch", "purity"})
List<GoldRate> findByCompanyId(Long companyId);
    @EntityGraph(attributePaths = {"company", "branch", "purity"})
    List<GoldRate> findByCompanyIdAndBranchIdAndPurityIdOrderByRateDateDesc(Long companyId, Long branchId, Long purityId);
    @EntityGraph(attributePaths = {"company", "branch", "purity"})
    List<GoldRate> findByCompanyIdAndBranchIdAndRateDateOrderByRatePerGramDesc(Long companyId, Long branchId, LocalDate rateDate);
    Optional<GoldRate> findByCompanyIdAndBranchIdAndPurityIdAndRateDate(Long companyId, Long branchId, Long purityId, LocalDate rateDate);
    @Override
    @EntityGraph(attributePaths = {"company", "branch", "purity"})
    Optional<GoldRate> findById(Long id);

}

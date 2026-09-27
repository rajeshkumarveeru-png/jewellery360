package com.jewellery360.repository;

import com.jewellery360.domain.GoldRate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface GoldRateRepository extends JpaRepository<GoldRate, Long> {
    List<GoldRate> findByCompanyId(Long companyId);
    List<GoldRate> findByCompanyIdAndBranchIdAndPurityIdOrderByRateDateDesc(Long companyId, Long branchId, Long purityId);
}

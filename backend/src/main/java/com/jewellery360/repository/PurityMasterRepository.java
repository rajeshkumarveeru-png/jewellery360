package com.jewellery360.repository;

import com.jewellery360.domain.PurityMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurityMasterRepository extends JpaRepository<PurityMaster, Long> {
    List<PurityMaster> findByCompanyIdOrderByMetalAscFinenessDesc(Long companyId);
    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);
    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);
    boolean existsByCompanyId(Long companyId);
}

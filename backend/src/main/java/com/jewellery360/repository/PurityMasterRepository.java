package com.jewellery360.repository;

import com.jewellery360.domain.PurityMaster;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurityMasterRepository extends JpaRepository<PurityMaster, Long> {

    @EntityGraph(attributePaths = {"company"})
    List<PurityMaster> findByCompanyId(Long companyId);

    @Override
    @EntityGraph(attributePaths = {"company"})
    Optional<PurityMaster> findById(Long id);

    boolean existsByCompanyIdAndNameIgnoreCase(
            Long companyId,
            String name
    );

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(
            Long companyId,
            String name,
            Long id
    );
}
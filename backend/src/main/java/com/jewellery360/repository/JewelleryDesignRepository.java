package com.jewellery360.repository;

import com.jewellery360.domain.JewelleryDesign;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JewelleryDesignRepository extends JpaRepository<JewelleryDesign, Long> {
    @EntityGraph(attributePaths = "category")
    List<JewelleryDesign> findByCompanyIdOrderByNameAsc(Long companyId);
    Optional<JewelleryDesign> findByCompanyIdAndCodeIgnoreCase(Long companyId, String code);
    Optional<JewelleryDesign> findFirstByCompanyIdAndNameIgnoreCase(Long companyId, String name);
}

package com.jewellery360.repository;

import com.jewellery360.domain.JewelleryDesign;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JewelleryDesignRepository extends JpaRepository<JewelleryDesign, Long> {
    @EntityGraph(attributePaths = {"company", "category", "category.company"})
List<JewelleryDesign> findByCompanyId(Long companyId);
    @Override
    @EntityGraph(attributePaths = {"company", "category", "category.company"})
    Optional<JewelleryDesign> findById(Long id);

    boolean existsByCompanyIdAndCodeIgnoreCase(Long companyId, String code);
}

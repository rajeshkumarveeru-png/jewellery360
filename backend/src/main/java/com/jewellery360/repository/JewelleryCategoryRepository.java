package com.jewellery360.repository;

import com.jewellery360.domain.JewelleryCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JewelleryCategoryRepository extends JpaRepository<JewelleryCategory, Long> {
    @EntityGraph(attributePaths = {"company"})
List<JewelleryCategory> findByCompanyId(Long companyId);
    @Override
    @EntityGraph(attributePaths = {"company"})
    Optional<JewelleryCategory> findById(Long id);

    boolean existsByCompanyIdAndCodeIgnoreCase(Long companyId, String code);
}

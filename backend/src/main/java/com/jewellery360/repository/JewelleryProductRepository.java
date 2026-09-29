package com.jewellery360.repository;

import com.jewellery360.domain.JewelleryProduct;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JewelleryProductRepository extends JpaRepository<JewelleryProduct, Long> {
    @EntityGraph(attributePaths = {"company", "category", "category.company", "design", "design.category", "design.category.company"})
List<JewelleryProduct> findByCompanyId(Long companyId);
    @Override
    @EntityGraph(attributePaths = {"company", "category", "category.company", "design", "design.category", "design.category.company"})
    Optional<JewelleryProduct> findById(Long id);

    boolean existsByCompanyIdAndSkuIgnoreCase(Long companyId, String sku);
}

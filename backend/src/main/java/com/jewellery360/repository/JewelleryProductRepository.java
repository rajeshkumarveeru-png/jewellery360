package com.jewellery360.repository;

import com.jewellery360.domain.JewelleryProduct;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface JewelleryProductRepository extends JpaRepository<JewelleryProduct, Long> {
    @EntityGraph(attributePaths = {"category", "design"})
    List<JewelleryProduct> findByCompanyIdOrderByNameAsc(Long companyId);
    Optional<JewelleryProduct> findByCompanyIdAndSkuIgnoreCase(Long companyId, String sku);
    Optional<JewelleryProduct> findFirstByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    @Query("select p.sku from JewelleryProduct p where p.company.id = :companyId")
    List<String> skus(Long companyId);
}

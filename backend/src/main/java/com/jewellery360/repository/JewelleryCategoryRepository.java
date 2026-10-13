package com.jewellery360.repository;

import com.jewellery360.domain.JewelleryCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JewelleryCategoryRepository extends JpaRepository<JewelleryCategory, Long> {
    List<JewelleryCategory> findByCompanyIdOrderByNameAsc(Long companyId);
    Optional<JewelleryCategory> findByCompanyIdAndCodeIgnoreCase(Long companyId, String code);
    Optional<JewelleryCategory> findFirstByCompanyIdAndNameIgnoreCase(Long companyId, String name);
}

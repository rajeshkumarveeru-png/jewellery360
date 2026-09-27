package com.jewellery360.repository;

import com.jewellery360.domain.JewelleryCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JewelleryCategoryRepository extends JpaRepository<JewelleryCategory, Long> {
    List<JewelleryCategory> findByCompanyId(Long companyId);
}

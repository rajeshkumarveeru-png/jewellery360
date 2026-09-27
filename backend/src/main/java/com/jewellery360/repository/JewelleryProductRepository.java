package com.jewellery360.repository;

import com.jewellery360.domain.JewelleryProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JewelleryProductRepository extends JpaRepository<JewelleryProduct, Long> {
    List<JewelleryProduct> findByCompanyId(Long companyId);
}

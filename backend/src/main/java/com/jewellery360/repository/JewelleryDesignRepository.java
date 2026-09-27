package com.jewellery360.repository;

import com.jewellery360.domain.JewelleryDesign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JewelleryDesignRepository extends JpaRepository<JewelleryDesign, Long> {
    List<JewelleryDesign> findByCompanyId(Long companyId);
}

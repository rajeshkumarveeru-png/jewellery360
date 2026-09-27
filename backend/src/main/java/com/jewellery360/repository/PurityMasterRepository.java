package com.jewellery360.repository;

import com.jewellery360.domain.PurityMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PurityMasterRepository extends JpaRepository<PurityMaster, Long> {
    List<PurityMaster> findByCompanyId(Long companyId);
}

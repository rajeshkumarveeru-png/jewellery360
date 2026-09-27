package com.jewellery360.repository;

import com.jewellery360.domain.CustomerAdvance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CustomerAdvanceRepository extends JpaRepository<CustomerAdvance, Long> {
    List<CustomerAdvance> findByCompanyId(Long companyId);
}

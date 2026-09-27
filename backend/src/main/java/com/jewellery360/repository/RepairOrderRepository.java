package com.jewellery360.repository;

import com.jewellery360.domain.RepairOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface RepairOrderRepository extends JpaRepository<RepairOrder, Long> {
    List<RepairOrder> findByCompanyId(Long companyId);
}

package com.jewellery360.repository;

import com.jewellery360.domain.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByCompanyId(Long companyId);
}

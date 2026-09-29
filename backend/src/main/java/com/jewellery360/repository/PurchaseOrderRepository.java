package com.jewellery360.repository;

import com.jewellery360.domain.PurchaseOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    @EntityGraph(attributePaths = {"company", "branch", "supplier"})
List<PurchaseOrder> findByCompanyId(Long companyId);
    @Override
    @EntityGraph(attributePaths = {"company", "branch", "supplier"})
    Optional<PurchaseOrder> findById(Long id);

}

package com.jewellery360.repository;

import com.jewellery360.domain.RepairOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface RepairOrderRepository extends JpaRepository<RepairOrder, Long> {
    @EntityGraph(attributePaths = {"company", "branch", "customer"})
List<RepairOrder> findByCompanyId(Long companyId);
    @Override
    @EntityGraph(attributePaths = {"company", "branch", "customer"})
    Optional<RepairOrder> findById(Long id);

}

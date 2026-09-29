package com.jewellery360.repository;

import com.jewellery360.domain.CustomOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CustomOrderRepository extends JpaRepository<CustomOrder, Long> {
    @EntityGraph(attributePaths = {"company", "branch", "customer"})
List<CustomOrder> findByCompanyId(Long companyId);
    @Override
    @EntityGraph(attributePaths = {"company", "branch", "customer"})
    Optional<CustomOrder> findById(Long id);

}

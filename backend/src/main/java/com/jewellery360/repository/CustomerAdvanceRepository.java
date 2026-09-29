package com.jewellery360.repository;

import com.jewellery360.domain.CustomerAdvance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CustomerAdvanceRepository extends JpaRepository<CustomerAdvance, Long> {
    @EntityGraph(attributePaths = {"company", "branch", "customer"})
List<CustomerAdvance> findByCompanyId(Long companyId);
    @Override
    @EntityGraph(attributePaths = {"company", "branch", "customer"})
    Optional<CustomerAdvance> findById(Long id);

}

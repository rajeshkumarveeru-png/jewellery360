package com.jewellery360.repository;

import com.jewellery360.domain.Supplier;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    @EntityGraph(attributePaths = {"company"})
List<Supplier> findByCompanyId(Long companyId);
    @Override
    @EntityGraph(attributePaths = {"company"})
    Optional<Supplier> findById(Long id);

}

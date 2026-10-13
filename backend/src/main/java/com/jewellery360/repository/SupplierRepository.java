package com.jewellery360.repository;

import com.jewellery360.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findByCompanyIdOrderByNameAsc(Long companyId);
    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);
}

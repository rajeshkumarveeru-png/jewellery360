package com.jewellery360.repository;

import com.jewellery360.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findByCompanyId(Long companyId);
}

package com.jewellery360.repository;

import com.jewellery360.domain.CustomOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CustomOrderRepository extends JpaRepository<CustomOrder, Long> {
    List<CustomOrder> findByCompanyId(Long companyId);
}

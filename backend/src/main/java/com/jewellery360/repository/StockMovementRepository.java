package com.jewellery360.repository;

import com.jewellery360.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByCompanyId(Long companyId);
}

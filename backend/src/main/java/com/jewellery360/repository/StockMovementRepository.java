package com.jewellery360.repository;

import com.jewellery360.domain.StockMovement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    @EntityGraph(attributePaths = {"company", "branch", "jewelleryItem", "createdBy"})
List<StockMovement> findByCompanyId(Long companyId);
    @Override
    @EntityGraph(attributePaths = {"company", "branch", "jewelleryItem", "createdBy"})
    Optional<StockMovement> findById(Long id);

}

package com.jewellery360.repository;

import com.jewellery360.domain.StockMovement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    @EntityGraph(attributePaths = {"branch", "createdBy"})
    List<StockMovement> findByJewelleryItemIdOrderByCreatedAtAscIdAsc(Long itemId);
}

package com.jewellery360.repository;

import com.jewellery360.domain.PurchaseItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {
    @EntityGraph(attributePaths = {"jewelleryItem"})
    List<PurchaseItem> findByPurchaseIdOrderById(Long purchaseId);
    List<PurchaseItem> findByJewelleryItemId(Long itemId);
}

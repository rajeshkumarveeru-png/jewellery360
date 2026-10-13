package com.jewellery360.repository;

import com.jewellery360.domain.PurchaseReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseReturnItemRepository extends JpaRepository<PurchaseReturnItem, Long> {
    boolean existsByPurchaseItemId(Long purchaseItemId);
}

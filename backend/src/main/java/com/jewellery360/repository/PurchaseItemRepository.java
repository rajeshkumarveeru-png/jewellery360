package com.jewellery360.repository;
import com.jewellery360.domain.PurchaseItem; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PurchaseItemRepository extends JpaRepository<PurchaseItem,Long>{ List<PurchaseItem> findByPurchaseId(Long purchaseId); }

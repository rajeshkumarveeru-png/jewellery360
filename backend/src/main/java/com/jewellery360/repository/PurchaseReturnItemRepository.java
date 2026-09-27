package com.jewellery360.repository;
import com.jewellery360.domain.PurchaseReturnItem; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PurchaseReturnItemRepository extends JpaRepository<PurchaseReturnItem,Long>{ List<PurchaseReturnItem> findByReturnRecordId(Long returnId); }

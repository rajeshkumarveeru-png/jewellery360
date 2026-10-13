package com.jewellery360.repository;

import com.jewellery360.domain.PurchaseReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long> {
    List<PurchaseReturn> findByPurchaseIdOrderById(Long purchaseId);
    List<PurchaseReturn> findByPurchaseSupplierIdOrderByReturnDateAsc(Long supplierId);
}

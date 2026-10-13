package com.jewellery360.repository;

import com.jewellery360.domain.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id = :id")
    Optional<PurchaseOrder> lock(Long id);

    @EntityGraph(attributePaths = {"supplier", "branch"})
    @Query("select p from PurchaseOrder p where p.company.id = :companyId and (:branchId is null or p.branch.id = :branchId) and p.purchaseDate between :from and :to order by p.purchaseDate desc, p.id desc")
    List<PurchaseOrder> between(Long companyId, Long branchId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"branch"})
    List<PurchaseOrder> findBySupplierIdOrderByPurchaseDateAscIdAsc(Long supplierId);

    @Query("select coalesce(sum(p.total - p.returnedAmount - p.paidAmount),0) from PurchaseOrder p where p.company.id = :companyId and p.status = 'RECEIVED' and p.total - p.returnedAmount - p.paidAmount > 0")
    BigDecimal outstanding(Long companyId);

    @Query("select p.supplier.id, p.supplier.name, sum(p.total - p.returnedAmount - p.paidAmount) from PurchaseOrder p where p.company.id = :companyId and p.status = 'RECEIVED' and p.total - p.returnedAmount - p.paidAmount > 0 group by p.supplier.id, p.supplier.name order by 3 desc")
    List<Object[]> outstandingBySupplier(Long companyId);
}

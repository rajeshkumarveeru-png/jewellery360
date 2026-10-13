package com.jewellery360.repository;

import com.jewellery360.domain.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findBySaleIdOrderById(Long saleId);
    List<Payment> findByRepairOrderIdOrderById(Long repairId);
    List<Payment> findByAdvanceIdOrderById(Long advanceId);
    List<Payment> findByPurchaseIdOrderById(Long purchaseId);

    @EntityGraph(attributePaths = {"sale", "advance", "oldGold", "repairOrder"})
    List<Payment> findByCustomerIdOrderByPaymentDateAscIdAsc(Long customerId);

    @EntityGraph(attributePaths = {"purchase"})
    List<Payment> findBySupplierIdOrderByPaymentDateAscIdAsc(Long supplierId);

    List<Payment> findByKarigarIdOrderByPaymentDateAscIdAsc(Long karigarId);

    @EntityGraph(attributePaths = {"customer", "supplier", "karigar", "sale", "branch", "createdBy"})
    @Query("select p from Payment p where p.company.id = :companyId and (:branchId is null or p.branch.id = :branchId) and p.paymentDate between :from and :to order by p.paymentDate desc, p.id desc")
    List<Payment> between(Long companyId, Long branchId, LocalDate from, LocalDate to);
}

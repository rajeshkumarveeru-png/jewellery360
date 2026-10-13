package com.jewellery360.repository;

import com.jewellery360.domain.Sale;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> lock(Long id);

    Optional<Sale> findByCompanyIdAndClientRef(Long companyId, String clientRef);

    boolean existsByCompanyIdAndInvoiceNoIgnoreCase(Long companyId, String invoiceNo);

    @EntityGraph(attributePaths = {"customer", "branch", "createdBy"})
    @Query("""
            select s from Sale s where s.company.id = :companyId and (:branchId is null or s.branch.id = :branchId)
              and s.saleDate between :from and :to
              and (:status is null or s.status = :status)
              and (:q is null or lower(s.invoiceNo) like :q or lower(s.customer.name) like :q or s.customer.phone like :q)
            order by s.saleDate desc, s.id desc
            """)
    List<Sale> list(Long companyId, Long branchId, LocalDate from, LocalDate to, String status, String q, Pageable page);

    @EntityGraph(attributePaths = {"customer", "branch"})
    List<Sale> findByCustomerIdOrderBySaleDateDescIdDesc(Long customerId);

    /** Completed (not draft / cancelled) bills of a customer that still have money due, oldest first. */
    @Query("""
            select s from Sale s where s.customer.id = :customerId and s.status in ('COMPLETED','PARTIALLY_RETURNED')
              and s.total - s.returnedAmount - s.paidAmount > 0 order by s.saleDate, s.id
            """)
    List<Sale> unpaidForCustomer(Long customerId);

    @EntityGraph(attributePaths = {"customer", "branch"})
    @Query("""
            select s from Sale s where s.company.id = :companyId and (:branchId is null or s.branch.id = :branchId)
              and s.status in ('COMPLETED','PARTIALLY_RETURNED') and s.total - s.returnedAmount - s.paidAmount > 0
            order by s.saleDate, s.id
            """)
    List<Sale> unpaid(Long companyId, Long branchId);

    @EntityGraph(attributePaths = {"customer", "branch"})
    @Query("""
            select s from Sale s where s.company.id = :companyId and (:branchId is null or s.branch.id = :branchId)
              and s.status not in ('DRAFT','CANCELLED') and s.saleDate between :from and :to
            """)
    List<Sale> completedBetween(Long companyId, Long branchId, LocalDate from, LocalDate to);

    @Query("""
            select coalesce(sum(s.total - s.returnedAmount - s.paidAmount), 0) from Sale s where s.company.id = :companyId
              and (:branchId is null or s.branch.id = :branchId) and s.status in ('COMPLETED','PARTIALLY_RETURNED')
              and s.total - s.returnedAmount - s.paidAmount > 0
            """)
    BigDecimal outstanding(Long companyId, Long branchId);

    @Query("""
            select coalesce(sum(s.total - s.returnedAmount - s.paidAmount), 0) from Sale s where s.customer.id = :customerId
              and s.status in ('COMPLETED','PARTIALLY_RETURNED')
            """)
    BigDecimal customerBalance(Long customerId);

    long countByCompanyIdAndStatus(Long companyId, String status);

    /** [customerId, amount owed] for customers who owe money. */
    @Query("""
            select s.customer.id, sum(s.total - s.returnedAmount - s.paidAmount) from Sale s where s.company.id = :companyId
              and s.status in ('COMPLETED','PARTIALLY_RETURNED') group by s.customer.id having sum(s.total - s.returnedAmount - s.paidAmount) > 0
            """)
    List<Object[]> balancesByCustomer(Long companyId);

    /** [customerId, count, total value, last bill date]. */
    @Query("""
            select s.customer.id, count(s), sum(s.total - s.returnedAmount), max(s.saleDate) from Sale s where s.company.id = :companyId
              and s.status not in ('DRAFT','CANCELLED') group by s.customer.id
            """)
    List<Object[]> purchaseStatsByCustomer(Long companyId);
}

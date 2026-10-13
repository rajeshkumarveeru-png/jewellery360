package com.jewellery360.repository;

import com.jewellery360.domain.CustomerAdvance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CustomerAdvanceRepository extends JpaRepository<CustomerAdvance, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CustomerAdvance a where a.id = :id")
    Optional<CustomerAdvance> lock(Long id);

    @EntityGraph(attributePaths = {"customer", "customOrder"})
    List<CustomerAdvance> findByCustomerIdOrderByAdvanceDateAscIdAsc(Long customerId);

    @EntityGraph(attributePaths = {"customer", "customOrder"})
    @Query("select a from CustomerAdvance a where a.company.id = :companyId and (:branchId is null or a.branch.id = :branchId) and (:openOnly = false or a.status = 'OPEN') order by a.advanceDate desc, a.id desc")
    List<CustomerAdvance> list(Long companyId, Long branchId, boolean openOnly);

    List<CustomerAdvance> findByCustomOrderId(Long orderId);

    @Query("select coalesce(sum(a.balanceAmount),0) from CustomerAdvance a where a.customer.id = :customerId and a.status = 'OPEN'")
    BigDecimal customerBalance(Long customerId);

    @Query("select coalesce(sum(a.balanceAmount),0) from CustomerAdvance a where a.company.id = :companyId and (:branchId is null or a.branch.id = :branchId) and a.status = 'OPEN'")
    BigDecimal openBalance(Long companyId, Long branchId);

    @Query("select a.customer.id, sum(a.balanceAmount) from CustomerAdvance a where a.company.id = :companyId and a.status = 'OPEN' group by a.customer.id")
    List<Object[]> balancesByCustomer(Long companyId);
}

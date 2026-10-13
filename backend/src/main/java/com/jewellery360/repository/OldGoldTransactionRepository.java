package com.jewellery360.repository;

import com.jewellery360.domain.OldGoldTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OldGoldTransactionRepository extends JpaRepository<OldGoldTransaction, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OldGoldTransaction t where t.id = :id")
    Optional<OldGoldTransaction> lock(Long id);

    @EntityGraph(attributePaths = {"customer"})
    @Query("select t from OldGoldTransaction t where t.company.id = :companyId and (:branchId is null or t.branch.id = :branchId) and t.transactionDate between :from and :to order by t.transactionDate desc, t.id desc")
    List<OldGoldTransaction> between(Long companyId, Long branchId, LocalDate from, LocalDate to);

    List<OldGoldTransaction> findByCustomerIdOrderByTransactionDateDesc(Long customerId);
}

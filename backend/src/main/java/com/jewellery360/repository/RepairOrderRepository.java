package com.jewellery360.repository;

import com.jewellery360.domain.RepairOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RepairOrderRepository extends JpaRepository<RepairOrder, Long> {
    @EntityGraph(attributePaths = {"customer", "karigar"})
    @Query("select r from RepairOrder r where r.company.id = :companyId and (:branchId is null or r.branch.id = :branchId) and (:status is null or r.status = :status) order by r.receivedDate desc, r.id desc")
    List<RepairOrder> list(Long companyId, Long branchId, String status);

    List<RepairOrder> findByCustomerIdOrderByReceivedDateDesc(Long customerId);

    @Query("select count(r) from RepairOrder r where r.company.id = :companyId and (:branchId is null or r.branch.id = :branchId) and r.status in ('RECEIVED','IN_REPAIR','READY')")
    long countPending(Long companyId, Long branchId);
}

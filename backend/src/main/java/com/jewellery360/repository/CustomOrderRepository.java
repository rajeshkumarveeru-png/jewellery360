package com.jewellery360.repository;

import com.jewellery360.domain.CustomOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomOrderRepository extends JpaRepository<CustomOrder, Long> {
    @EntityGraph(attributePaths = {"customer", "purity", "karigar"})
    @Query("select o from CustomOrder o where o.company.id = :companyId and (:branchId is null or o.branch.id = :branchId) and (:status is null or o.status = :status) order by o.orderDate desc, o.id desc")
    List<CustomOrder> list(Long companyId, Long branchId, String status);

    @EntityGraph(attributePaths = {"purity"})
    List<CustomOrder> findByCustomerIdOrderByOrderDateDesc(Long customerId);

    @Query("select count(o) from CustomOrder o where o.company.id = :companyId and (:branchId is null or o.branch.id = :branchId) and o.status in ('ORDERED','IN_PROGRESS','READY')")
    long countPending(Long companyId, Long branchId);
}

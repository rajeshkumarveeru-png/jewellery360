package com.jewellery360.repository;

import com.jewellery360.domain.StockTransfer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    @EntityGraph(attributePaths = {"fromBranch", "toBranch", "createdBy"})
    @Query("select t from StockTransfer t where t.company.id = :companyId and (:branchId is null or t.fromBranch.id = :branchId or t.toBranch.id = :branchId) order by t.transferDate desc, t.id desc")
    List<StockTransfer> list(Long companyId, Long branchId);
}

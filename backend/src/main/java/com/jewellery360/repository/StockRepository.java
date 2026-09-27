package com.jewellery360.repository;
import org.springframework.data.repository.query.Param;

import com.jewellery360.domain.Stock;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface StockRepository extends JpaRepository<Stock, Long> {
    List<Stock> findByCompanyId(Long companyId);
    Optional<Stock> findByBranchIdAndJewelleryItemId(Long branchId, Long jewelleryItemId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.id = :id")
    Optional<Stock> findByIdForUpdate(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.branch.id = :branchId and s.jewelleryItem.id = :itemId")
    Optional<Stock> findByBranchAndItemForUpdate(@Param("branchId") Long branchId, @Param("itemId") Long itemId);
}

package com.jewellery360.repository;

import com.jewellery360.domain.StockTransfer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    @EntityGraph(attributePaths = {"company", "fromBranch", "toBranch"})
List<StockTransfer> findByCompanyId(Long companyId);
    @Override
    @EntityGraph(attributePaths = {"company", "fromBranch", "toBranch"})
    Optional<StockTransfer> findById(Long id);

}

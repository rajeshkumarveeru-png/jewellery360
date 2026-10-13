package com.jewellery360.repository;

import com.jewellery360.domain.StockTransferItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransferItemRepository extends JpaRepository<StockTransferItem, Long> {
    @EntityGraph(attributePaths = {"jewelleryItem"})
    List<StockTransferItem> findByTransferId(Long transferId);
}

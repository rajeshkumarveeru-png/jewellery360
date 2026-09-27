package com.jewellery360.repository;
import com.jewellery360.domain.StockTransferItem; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface StockTransferItemRepository extends JpaRepository<StockTransferItem,Long>{ List<StockTransferItem> findByTransferId(Long transferId); }

package com.jewellery360.repository;
import com.jewellery360.domain.SaleReturnItem; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SaleReturnItemRepository extends JpaRepository<SaleReturnItem,Long>{ boolean existsBySaleItemId(Long saleItemId); List<SaleReturnItem> findByReturnRecordId(Long returnId); }

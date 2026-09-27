package com.jewellery360.repository;
import com.jewellery360.domain.SaleItem; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SaleItemRepository extends JpaRepository<SaleItem,Long>{ List<SaleItem> findBySaleId(Long saleId); }

package com.jewellery360.repository;

import com.jewellery360.domain.SaleItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SaleItemRepository extends JpaRepository<SaleItem,Long>{
    @EntityGraph(attributePaths = {"jewelleryItem", "jewelleryItem.product", "jewelleryItem.product.design", "jewelleryItem.tag", "jewelleryItem.purity"})
    List<SaleItem> findBySaleId(Long saleId);
}

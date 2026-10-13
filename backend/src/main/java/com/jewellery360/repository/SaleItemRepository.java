package com.jewellery360.repository;

import com.jewellery360.domain.SaleItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    @EntityGraph(attributePaths = {"jewelleryItem", "jewelleryItem.product"})
    List<SaleItem> findBySaleIdOrderById(Long saleId);

    @EntityGraph(attributePaths = {"sale"})
    List<SaleItem> findByJewelleryItemIdOrderByIdDesc(Long itemId);

    /** Lines of valid bills in a period (for jewellery and profit reports). */
    @EntityGraph(attributePaths = {"sale", "jewelleryItem", "jewelleryItem.product", "jewelleryItem.product.category"})
    @Query("""
            select si from SaleItem si where si.sale.company.id = :companyId and (:branchId is null or si.sale.branch.id = :branchId)
              and si.sale.status not in ('DRAFT','CANCELLED') and si.sale.saleDate between :from and :to
              and not exists (select r from SaleReturnItem r where r.saleItem = si)
            """)
    List<SaleItem> soldBetween(Long companyId, Long branchId, LocalDate from, LocalDate to);
}

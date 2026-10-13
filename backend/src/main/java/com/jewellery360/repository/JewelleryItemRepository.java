package com.jewellery360.repository;

import com.jewellery360.domain.JewelleryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface JewelleryItemRepository extends JpaRepository<JewelleryItem, Long> {
    String GRAPH_PRODUCT = "product";

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from JewelleryItem i where i.id = :id")
    Optional<JewelleryItem> lock(Long id);

    /** Items of a company, optionally one branch and one status, newest first. */
    @EntityGraph(attributePaths = {"product", "product.category", "product.design", "purity", "branch"})
    @Query("""
            select i from JewelleryItem i where i.company.id = :companyId
              and (:branchId is null or i.branch.id = :branchId)
              and (:status is null or i.status = :status)
            order by i.id desc
            """)
    List<JewelleryItem> list(Long companyId, Long branchId, String status, Pageable page);

    @EntityGraph(attributePaths = {"product", "product.category", "product.design", "purity", "branch"})
    @Query("""
            select i from JewelleryItem i where i.company.id = :companyId
              and (:branchId is null or i.branch.id = :branchId)
              and (lower(i.tagNo) like :q or lower(i.barcode) like :q or lower(coalesce(i.huid,'')) like :q
                   or lower(i.product.name) like :q or lower(i.product.sku) like :q)
            order by case when i.status = 'IN_STOCK' then 0 else 1 end, i.id desc
            """)
    List<JewelleryItem> search(Long companyId, Long branchId, String q, Pageable page);

    @EntityGraph(attributePaths = {"product", "purity", "branch"})
    @Query("""
            select i from JewelleryItem i where i.company.id = :companyId
              and (lower(i.barcode) = lower(:code) or lower(i.tagNo) = lower(:code) or lower(coalesce(i.huid,'')) = lower(:code))
            """)
    List<JewelleryItem> findByCode(Long companyId, String code);

    boolean existsByCompanyIdAndTagNoIgnoreCase(Long companyId, String tagNo);
    boolean existsByCompanyIdAndBarcode(Long companyId, String barcode);
    boolean existsByCompanyIdAndTagNoIgnoreCaseAndIdNot(Long companyId, String tagNo, Long id);
    boolean existsByCompanyIdAndBarcodeAndIdNot(Long companyId, String barcode, Long id);

    @Query("select i.tagNo from JewelleryItem i where i.company.id = :companyId")
    List<String> tagNumbers(Long companyId);

    /** Stock totals per purity: [purityName, metal, fineness, count, grossWeight, netWeight, costValue]. */
    @Query("""
            select i.purity.name, i.purity.metal, i.purity.fineness, count(i), sum(i.grossWeight), sum(i.netWeight), sum(i.costPrice)
            from JewelleryItem i where i.company.id = :companyId and (:branchId is null or i.branch.id = :branchId) and i.status = 'IN_STOCK'
            group by i.purity.name, i.purity.metal, i.purity.fineness order by i.purity.metal, i.purity.fineness desc
            """)
    List<Object[]> stockByPurity(Long companyId, Long branchId);

    @Query("""
            select i.branch.name, count(i), sum(i.netWeight) from JewelleryItem i
            where i.company.id = :companyId and i.status = 'IN_STOCK' group by i.branch.name order by i.branch.name
            """)
    List<Object[]> stockByBranch(Long companyId);

    @Query("""
            select i.product.category.name, count(i), sum(i.netWeight) from JewelleryItem i
            where i.company.id = :companyId and (:branchId is null or i.branch.id = :branchId) and i.status = 'IN_STOCK'
            group by i.product.category.name order by count(i) desc
            """)
    List<Object[]> stockByCategory(Long companyId, Long branchId);

    long countByCompanyIdAndStatus(Long companyId, String status);

    @Query("select coalesce(sum(i.netWeight),0) from JewelleryItem i where i.company.id = :companyId and (:branchId is null or i.branch.id = :branchId) and i.status = 'IN_STOCK'")
    BigDecimal inStockNetWeight(Long companyId, Long branchId);
}

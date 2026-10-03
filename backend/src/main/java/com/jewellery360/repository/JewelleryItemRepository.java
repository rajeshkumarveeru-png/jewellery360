package com.jewellery360.repository;
import org.springframework.data.repository.query.Param;

import com.jewellery360.domain.JewelleryItem;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface JewelleryItemRepository extends JpaRepository<JewelleryItem, Long> {
    @EntityGraph(attributePaths = {"company", "branch", "product", "product.category", "product.category.company", "product.design", "product.design.category", "product.design.category.company", "tag", "tag.company", "tag.branch", "purity", "purity.company"})
List<JewelleryItem> findByCompanyId(Long companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from JewelleryItem i where i.id = :id")
    Optional<JewelleryItem> findByIdForUpdate(@Param("id") Long id);
    boolean existsByTagIdAndIdNot(Long tagId, Long id);
    @Override
    @EntityGraph(attributePaths = {"company", "branch", "product", "product.category", "product.category.company", "product.design", "product.design.category", "product.design.category.company", "tag", "tag.company", "tag.branch", "purity", "purity.company"})
    Optional<JewelleryItem> findById(Long id);

}

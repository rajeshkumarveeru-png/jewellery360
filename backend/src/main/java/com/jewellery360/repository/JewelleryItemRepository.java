package com.jewellery360.repository;
import org.springframework.data.repository.query.Param;

import com.jewellery360.domain.JewelleryItem;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface JewelleryItemRepository extends JpaRepository<JewelleryItem, Long> {
    List<JewelleryItem> findByCompanyId(Long companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from JewelleryItem i where i.id = :id")
    Optional<JewelleryItem> findByIdForUpdate(@Param("id") Long id);
}

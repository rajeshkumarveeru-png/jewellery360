package com.jewellery360.repository;
import org.springframework.data.repository.query.Param;

import com.jewellery360.domain.Sale;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    List<Sale> findByCompanyId(Long companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);
}

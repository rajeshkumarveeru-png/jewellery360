package com.jewellery360.repository;
import org.springframework.data.repository.query.Param;

import com.jewellery360.domain.Sale;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    @EntityGraph(attributePaths = {"company", "branch", "customer", "createdBy"})
List<Sale> findByCompanyId(Long companyId);
    /** Sales of one company in an inclusive date range (used by the analytics widgets and trend charts). */
    @EntityGraph(attributePaths = {"branch"})
    List<Sale> findByCompanyIdAndSaleDateBetween(Long companyId, java.time.LocalDate from, java.time.LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);
    @Override
    @EntityGraph(attributePaths = {"company", "branch", "customer", "createdBy"})
    Optional<Sale> findById(Long id);

}

package com.jewellery360.repository;

import com.jewellery360.domain.DayClose;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DayCloseRepository extends JpaRepository<DayClose, Long> {
    Optional<DayClose> findByBranchIdAndCloseDate(Long branchId, LocalDate date);
    Optional<DayClose> findFirstByBranchIdAndCloseDateBeforeOrderByCloseDateDesc(Long branchId, LocalDate date);
    @EntityGraph(attributePaths = {"branch", "createdBy"})
    List<DayClose> findTop30ByBranchIdOrderByCloseDateDesc(Long branchId);
}

package com.jewellery360.repository;

import com.jewellery360.domain.JobMetalMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JobMetalMovementRepository extends JpaRepository<JobMetalMovement, Long> {
    List<JobMetalMovement> findByJobCardIdOrderByMovementDateAscIdAsc(Long jobCardId);

    /** [jobCardId, movementType, sum(weight), sum(fineWeight)] for the given jobs. */
    @Query("select m.jobCard.id, m.movementType, sum(m.weight), sum(m.fineWeight) from JobMetalMovement m where m.jobCard.id in :jobIds group by m.jobCard.id, m.movementType")
    List<Object[]> totals(List<Long> jobIds);
}

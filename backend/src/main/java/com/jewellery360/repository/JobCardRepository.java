package com.jewellery360.repository;

import com.jewellery360.domain.JobCard;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JobCardRepository extends JpaRepository<JobCard, Long> {
    @EntityGraph(attributePaths = {"karigar", "purity", "customOrder", "repairOrder"})
    @Query("select j from JobCard j where j.company.id = :companyId and (:branchId is null or j.branch.id = :branchId) and (:karigarId is null or j.karigar.id = :karigarId) and (:status is null or j.status = :status) order by j.jobDate desc, j.id desc")
    List<JobCard> list(Long companyId, Long branchId, Long karigarId, String status);
}

package com.jewellery360.repository;

import com.jewellery360.domain.AuditHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditHistoryRepository extends JpaRepository<AuditHistory, Long> {

    @EntityGraph(attributePaths = {"user", "company", "branch"})
    List<AuditHistory> findTop200ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"user", "company", "branch"})
    List<AuditHistory> findTop200ByCompanyIdOrderByCreatedAtDesc(Long companyId);
}

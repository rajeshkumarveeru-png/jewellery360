package com.jewellery360.repository;

import com.jewellery360.domain.AuditHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditHistoryRepository extends JpaRepository<AuditHistory, Long> {
    @EntityGraph(attributePaths = {"user", "company", "branch"})
    List<AuditHistory> findByOrderByCreatedAtDesc(Pageable page);

    @EntityGraph(attributePaths = {"user", "company", "branch"})
    List<AuditHistory> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable page);

    @EntityGraph(attributePaths = {"user", "company", "branch"})
    List<AuditHistory> findByCompanyIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(Long companyId, String entityType, Long entityId);
}

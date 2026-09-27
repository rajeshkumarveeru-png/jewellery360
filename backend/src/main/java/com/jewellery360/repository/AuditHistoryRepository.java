package com.jewellery360.repository;
import com.jewellery360.domain.AuditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AuditHistoryRepository extends JpaRepository<AuditHistory,Long> {
    List<AuditHistory> findTop200ByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<AuditHistory> findTop200ByOrderByCreatedAtDesc();
}

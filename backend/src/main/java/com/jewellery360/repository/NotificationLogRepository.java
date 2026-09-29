package com.jewellery360.repository;

import com.jewellery360.domain.NotificationLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    @EntityGraph(attributePaths = {"company", "branch", "user"})
List<NotificationLog> findByCompanyId(Long companyId);
    @Override
    @EntityGraph(attributePaths = {"company", "branch", "user"})
    Optional<NotificationLog> findById(Long id);

}

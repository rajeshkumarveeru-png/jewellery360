package com.jewellery360.repository;

import com.jewellery360.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByCompanyId(Long companyId);
}

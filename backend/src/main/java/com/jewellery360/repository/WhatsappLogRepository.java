package com.jewellery360.repository;

import com.jewellery360.domain.WhatsappLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface WhatsappLogRepository extends JpaRepository<WhatsappLog, Long> {
    List<WhatsappLog> findByCompanyId(Long companyId);
}

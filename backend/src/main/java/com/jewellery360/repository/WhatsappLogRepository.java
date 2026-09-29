package com.jewellery360.repository;

import com.jewellery360.domain.WhatsappLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface WhatsappLogRepository extends JpaRepository<WhatsappLog, Long> {
    @EntityGraph(attributePaths = {"company", "branch", "customer"})
List<WhatsappLog> findByCompanyId(Long companyId);
    @Override
    @EntityGraph(attributePaths = {"company", "branch", "customer"})
    Optional<WhatsappLog> findById(Long id);

}

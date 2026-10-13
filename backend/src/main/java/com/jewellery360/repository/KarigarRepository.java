package com.jewellery360.repository;

import com.jewellery360.domain.Karigar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KarigarRepository extends JpaRepository<Karigar, Long> {
    List<Karigar> findByCompanyIdOrderByNameAsc(Long companyId);
    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);
}

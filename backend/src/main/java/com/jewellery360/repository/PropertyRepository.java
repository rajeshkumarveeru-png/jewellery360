package com.jewellery360.repository;

import com.jewellery360.domain.Property;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PropertyRepository extends JpaRepository<Property,Long> {
    Optional<Property> findByCompanyIdAndKeyAndActiveTrue(Long companyId,String key);
    Optional<Property> findByCompanyIdAndUserIdAndKeyAndActiveTrue(Long companyId, Long userId, String key);
}

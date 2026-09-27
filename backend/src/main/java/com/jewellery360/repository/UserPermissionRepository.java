package com.jewellery360.repository;

import com.jewellery360.domain.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {
    Optional<UserPermission> findByUserIdAndPermission_Code(Long userId, String code);
}

package com.jewellery360.repository;

import com.jewellery360.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RoleRepository extends JpaRepository<Role, Long> {
}

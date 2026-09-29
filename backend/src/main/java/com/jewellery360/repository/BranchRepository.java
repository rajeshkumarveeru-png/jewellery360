package com.jewellery360.repository;

import com.jewellery360.domain.Branch;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    @Override
    @EntityGraph(attributePaths = "company")
    List<Branch> findAll();

    @EntityGraph(attributePaths = "company")
    List<Branch> findByCompanyId(Long companyId);

    @Override
    @EntityGraph(attributePaths = "company")
    Optional<Branch> findById(Long id);
}

package com.jewellery360.repository;

import com.jewellery360.domain.Customer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @EntityGraph(attributePaths = {"company", "branch"})
    List<Customer> findByCompanyId(Long companyId);

    @Override
    @EntityGraph(attributePaths = {"company", "branch"})
    Optional<Customer> findById(Long id);
}

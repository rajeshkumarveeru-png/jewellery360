package com.jewellery360.repository;

import com.jewellery360.domain.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @EntityGraph(attributePaths = {"company", "branch", "customer", "sale", "purchase", "createdBy"})
    List<Payment> findByCompanyId(Long companyId);

    @EntityGraph(attributePaths = {"company", "branch", "customer", "sale", "purchase", "createdBy"})
    List<Payment> findBySaleId(Long saleId);

    @EntityGraph(attributePaths = {"company", "branch", "customer", "sale", "purchase", "createdBy"})
    List<Payment> findByCustomerId(Long customerId);

    @Override
    @EntityGraph(attributePaths = {"company", "branch", "customer", "sale", "purchase", "createdBy"})
    Optional<Payment> findById(Long id);
}

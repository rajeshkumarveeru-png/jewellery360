package com.jewellery360.repository;

import com.jewellery360.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByCompanyId(Long companyId);
}

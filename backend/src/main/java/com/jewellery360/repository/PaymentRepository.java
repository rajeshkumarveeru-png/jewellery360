package com.jewellery360.repository;
import com.jewellery360.domain.Payment; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PaymentRepository extends JpaRepository<Payment,Long>{ List<Payment> findByCompanyId(Long companyId); List<Payment> findBySaleId(Long saleId); List<Payment> findByCustomerId(Long customerId); }

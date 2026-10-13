package com.jewellery360.repository;

import com.jewellery360.domain.Customer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query("""
            select c from Customer c where c.company.id = :companyId and c.active = true
              and (:q is null or lower(c.name) like :q or c.phone like :q or lower(coalesce(c.email,'')) like :q or lower(coalesce(c.gstin,'')) like :q)
            order by c.name
            """)
    List<Customer> search(Long companyId, String q, Pageable page);

    List<Customer> findByCompanyIdAndPhone(Long companyId, String phone);

    long countByCompanyIdAndActiveTrue(Long companyId);

    @Query("""
            select c from Customer c where c.company.id = :companyId and c.active = true and (
              (extract(month from c.birthday) = :month and extract(day from c.birthday) = :day) or
              (extract(month from c.anniversary) = :month and extract(day from c.anniversary) = :day))
            """)
    List<Customer> celebrating(Long companyId, int month, int day);
}

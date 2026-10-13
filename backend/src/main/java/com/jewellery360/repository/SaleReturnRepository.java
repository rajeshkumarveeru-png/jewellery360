package com.jewellery360.repository;

import com.jewellery360.domain.SaleReturn;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {
    List<SaleReturn> findBySaleIdOrderById(Long saleId);

    @EntityGraph(attributePaths = {"sale", "sale.customer"})
    @Query("select r from SaleReturn r where r.company.id = :companyId and (:branchId is null or r.branch.id = :branchId) and r.returnDate between :from and :to order by r.returnDate desc")
    List<SaleReturn> between(Long companyId, Long branchId, LocalDate from, LocalDate to);

    @Query("select r from SaleReturn r where r.sale.customer.id = :customerId order by r.returnDate")
    List<SaleReturn> forCustomer(Long customerId);
}

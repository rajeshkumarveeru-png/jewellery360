package com.jewellery360.repository;
import com.jewellery360.domain.SaleReturn; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SaleReturnRepository extends JpaRepository<SaleReturn,Long>{ List<SaleReturn> findByCompanyId(Long companyId); boolean existsBySaleId(Long saleId); }

package com.jewellery360.repository;

import com.jewellery360.domain.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    List<StockTransfer> findByCompanyId(Long companyId);
}

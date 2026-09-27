package com.jewellery360.repository;

import com.jewellery360.domain.OldGoldTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface OldGoldTransactionRepository extends JpaRepository<OldGoldTransaction, Long> {
    List<OldGoldTransaction> findByCompanyId(Long companyId);
}

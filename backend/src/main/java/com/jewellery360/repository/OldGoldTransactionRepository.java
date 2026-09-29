package com.jewellery360.repository;

import com.jewellery360.domain.OldGoldTransaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface OldGoldTransactionRepository extends JpaRepository<OldGoldTransaction, Long> {
    @EntityGraph(attributePaths = {"company", "branch", "customer"})
List<OldGoldTransaction> findByCompanyId(Long companyId);
    @Override
    @EntityGraph(attributePaths = {"company", "branch", "customer"})
    Optional<OldGoldTransaction> findById(Long id);

}

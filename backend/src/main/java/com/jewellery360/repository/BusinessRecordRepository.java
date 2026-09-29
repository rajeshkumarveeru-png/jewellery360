package com.jewellery360.repository;

import com.jewellery360.domain.BusinessRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface BusinessRecordRepository extends JpaRepository<BusinessRecord,Long> {
    @EntityGraph(attributePaths = {"company", "branch"})
List<BusinessRecord> findByModuleOrderByRecordDateDescIdDesc(String module);
    @EntityGraph(attributePaths = {"company", "branch"})
List<BusinessRecord> findByCompanyIdAndModuleOrderByRecordDateDescIdDesc(Long companyId,String module);
    @EntityGraph(attributePaths = {"company", "branch"})
List<BusinessRecord> findByCompanyIdAndBranchIdAndModuleOrderByRecordDateDescIdDesc(Long companyId,Long branchId,String module);
    @EntityGraph(attributePaths = {"company", "branch"})
List<BusinessRecord> findByCompanyIdOrderByRecordDateDescIdDesc(Long companyId);
    @EntityGraph(attributePaths = {"company", "branch"})
List<BusinessRecord> findByCompanyIdAndBranchIdOrderByRecordDateDescIdDesc(Long companyId,Long branchId);
    long countByCompanyIdAndModule(Long companyId,String module);
}

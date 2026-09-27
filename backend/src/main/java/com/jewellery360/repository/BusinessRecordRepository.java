package com.jewellery360.repository;

import com.jewellery360.domain.BusinessRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface BusinessRecordRepository extends JpaRepository<BusinessRecord,Long> {
    List<BusinessRecord> findByModuleOrderByRecordDateDescIdDesc(String module);
    List<BusinessRecord> findByCompanyIdAndModuleOrderByRecordDateDescIdDesc(Long companyId,String module);
    List<BusinessRecord> findByCompanyIdAndBranchIdAndModuleOrderByRecordDateDescIdDesc(Long companyId,Long branchId,String module);
    List<BusinessRecord> findByCompanyIdOrderByRecordDateDescIdDesc(Long companyId);
    List<BusinessRecord> findByCompanyIdAndBranchIdOrderByRecordDateDescIdDesc(Long companyId,Long branchId);
    long countByCompanyIdAndModule(Long companyId,String module);
}

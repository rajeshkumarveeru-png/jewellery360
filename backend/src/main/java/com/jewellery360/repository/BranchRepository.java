package com.jewellery360.repository;
import com.jewellery360.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface BranchRepository extends JpaRepository<Branch,Long>{List<Branch> findByCompanyId(Long companyId);}

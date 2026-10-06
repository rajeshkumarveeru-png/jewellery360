package com.jewellery360.repository;
import com.jewellery360.domain.UserRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserRequestRepository extends JpaRepository<UserRequest,Long> {
    boolean existsByTargetUserIdAndRequestTypeAndStatus(Long id,String type,String status);
    List<UserRequest> findByStatusOrderByCreatedAtDesc(String status);
    List<UserRequest> findByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId,String status);
    List<UserRequest> findByCompanyIdAndRequestTypeOrderByIdDesc(Long companyId,String requestType);
    List<UserRequest> findByRequestTypeOrderByIdDesc(String requestType);
    long countByCompanyIdAndRequestTypeAndStatus(Long companyId,String requestType,String status);
    java.util.Optional<UserRequest> findFirstByCompanyIdAndRequestTypeAndStatusOrderByIdAsc(Long companyId,String requestType,String status);
}

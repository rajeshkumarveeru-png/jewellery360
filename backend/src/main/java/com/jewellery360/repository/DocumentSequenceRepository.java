package com.jewellery360.repository;

import com.jewellery360.domain.DocumentSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.company.id = :companyId and s.docKey = :docKey")
    Optional<DocumentSequence> lock(Long companyId, String docKey);

    Optional<DocumentSequence> findByCompanyIdAndDocKey(Long companyId, String docKey);
}

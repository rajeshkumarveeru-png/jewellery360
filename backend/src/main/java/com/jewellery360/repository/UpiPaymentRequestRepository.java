package com.jewellery360.repository;

import com.jewellery360.domain.UpiPaymentRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UpiPaymentRequestRepository extends JpaRepository<UpiPaymentRequest, Long> {
    /** Locks the request so a webhook and a status check can never both record the payment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from UpiPaymentRequest r where r.id = :id")
    Optional<UpiPaymentRequest> lock(Long id);

    Optional<UpiPaymentRequest> findFirstByProviderRef(String providerRef);

    List<UpiPaymentRequest> findBySaleIdOrderByIdDesc(Long saleId);

    List<UpiPaymentRequest> findBySaleIdAndStatus(Long saleId, String status);
}

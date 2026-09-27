package com.jewellery360.repository;
import com.jewellery360.domain.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface OtpChallengeRepository extends JpaRepository<OtpChallenge,Long> {
    Optional<OtpChallenge> findTopByUserIdAndConsumedFalseOrderByCreatedAtDesc(Long userId);
}

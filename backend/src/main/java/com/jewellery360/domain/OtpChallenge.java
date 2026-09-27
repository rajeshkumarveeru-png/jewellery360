package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="otp_challenge")
@Getter @Setter
public class OtpChallenge {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id", nullable=false) private AppUser user;
    @Column(nullable=false, length=64) private String otpHash;
    @Column(nullable=false) private Instant expiresAt;
    @Column(nullable=false) private int attempts = 0;
    @Column(nullable=false) private boolean consumed = false;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

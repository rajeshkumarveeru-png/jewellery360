package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="user_request")
@Getter @Setter
public class UserRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false, length=40) private String requestType;
    @Column(nullable=false, length=20) private String status;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="target_user_id") private AppUser targetUser;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="requested_by_id") private AppUser requestedBy;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="company_id") private Company company;
    @Column(length=500) private String message;
    @Column(length=255) private String pendingPasswordHash;
    @Column(nullable=false) private Instant createdAt = Instant.now();
    private Instant processedAt;
}

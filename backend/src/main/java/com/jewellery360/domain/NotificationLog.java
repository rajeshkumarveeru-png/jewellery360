package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="notification_log")
@Getter @Setter
public class NotificationLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id") private AppUser user;
    @Column(nullable=false,length=30) private String channel;
    @Column(nullable=false,length=80) private String notificationType;
    @Column(length=200) private String subject;
    @Column(nullable=false,length=4000) private String message;
    @Column(nullable=false,length=30) private String status;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="whatsapp_log")
@Getter @Setter
public class WhatsappLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="customer_id") private Customer customer;
    @Column(nullable=false,length=30) private String phone;
    @Column(nullable=false,length=50) private String messageType;
    @Column(length=50) private String referenceType;
    private Long referenceId;
    @Column(nullable=false,length=30) private String status;
    @Column(length=200) private String providerMessageId;
    @Column(length=2000) private String errorMessage;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

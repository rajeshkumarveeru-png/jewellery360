package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="customer",
    indexes={@Index(name="idx_customer_company_phone", columnList="company_id,phone")})
@Getter @Setter
public class Customer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Version
    @Column(nullable=false)
    private Long version = 0L;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @Column(nullable=false,length=180) private String name;
    @Column(length=25) private String phone;
    @Column(length=180) private String email;
    @Column(length=600) private String address;
    @Column(length=30) private String gstin;
    @Column(length=2000) private String notes;
    @Column(nullable=false) private boolean active = true;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

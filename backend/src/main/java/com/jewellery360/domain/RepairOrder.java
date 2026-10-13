package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** RECEIVED -> IN_REPAIR -> READY -> DELIVERED, or CANCELLED. Money taken is a payment linked to the repair. */
@Entity @Table(name="repair_order")
@Getter @Setter
public class RepairOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="customer_id") private Customer customer;
    @Column(nullable=false, length=80) private String orderNo;
    @Column(nullable=false) private LocalDate receivedDate;
    private LocalDate expectedDate;
    @Column(nullable=false, length=300) private String itemDescription;
    @Column(length=80) private String itemTag;
    @Column(length=20) private String huid;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal grossWeight = BigDecimal.ZERO;
    @Column(nullable=false, length=2000) private String problem;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal estimate = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal finalAmount = BigDecimal.ZERO;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="karigar_id") private Karigar karigar;
    @Column(nullable=false, length=30) private String status = "RECEIVED";
    private LocalDate deliveredDate;
    @Column(length=1000) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

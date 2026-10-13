package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Made-to-order jewellery. ORDERED -> IN_PROGRESS -> READY -> DELIVERED (final bill), or CANCELLED. Advances are CustomerAdvance rows linked here. */
@Entity @Table(name="custom_order")
@Getter @Setter
public class CustomOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="customer_id") private Customer customer;
    @Column(nullable=false, length=80) private String orderNo;
    @Column(nullable=false) private LocalDate orderDate;
    private LocalDate deliveryDate;
    @Column(nullable=false, length=2000) private String description;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="purity_id") private PurityMaster purity;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal targetWeight = BigDecimal.ZERO;
    @Column(length=300) private String makingNotes;
    /** Gold rate promised to the customer when the advance was taken (0 = bill at the rate of the delivery day). */
    @Column(nullable=false, precision=19, scale=3) private BigDecimal lockedRate = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal estimate = BigDecimal.ZERO;
    @Column(nullable=false, length=30) private String status = "ORDERED";
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="karigar_id") private Karigar karigar;
    @Column(length=1000) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

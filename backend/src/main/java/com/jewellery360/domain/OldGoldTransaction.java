package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Old gold taken from a customer. EXCHANGE: the value is adjusted against a new bill (payment mode OLD_GOLD).
 * PURCHASE: the shop pays the customer (payment type PAYOUT). status: OPEN (value still available) or SETTLED.
 */
@Entity @Table(name="old_gold_transaction")
@Getter @Setter
public class OldGoldTransaction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="customer_id") private Customer customer;
    @Column(nullable=false, length=80) private String transactionNo;
    @Column(nullable=false, length=30) private String transactionType;
    @Column(nullable=false) private LocalDate transactionDate;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal totalGrossWeight = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal totalNetWeight = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal totalAmount = BigDecimal.ZERO;
    /** Adjusted against bills or paid out. */
    @Column(nullable=false, precision=19, scale=3) private BigDecimal usedAmount = BigDecimal.ZERO;
    @Column(nullable=false, length=30) private String status = "OPEN";
    @Column(length=1000) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();

    public BigDecimal available() { return totalAmount.subtract(usedAmount); }
}

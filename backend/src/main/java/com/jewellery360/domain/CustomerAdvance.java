package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Money the shop holds for a customer: an advance receipt (optionally for an order) or a return credited to the customer.
 * balance = amount - used - refunded. Using it on a bill is a payment of mode ADVANCE linked to this record.
 */
@Entity @Table(name="customer_advance")
@Getter @Setter
public class CustomerAdvance {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="customer_id") private Customer customer;
    @Column(nullable=false, length=80) private String advanceNo;
    @Column(nullable=false) private LocalDate advanceDate;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal amount = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal usedAmount = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal refundedAmount = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal balanceAmount = BigDecimal.ZERO;
    /** RECEIPT (customer paid in) or RETURN (credit from a returned bill). */
    @Column(nullable=false, length=20) private String source = "RECEIPT";
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="custom_order_id") private CustomOrder customOrder;
    /** OPEN or CLOSED (fully used or refunded). */
    @Column(nullable=false, length=30) private String status = "OPEN";
    @Column(length=1000) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();

    public void recalc() {
        balanceAmount = amount.subtract(usedAmount).subtract(refundedAmount);
        status = balanceAmount.signum() > 0 ? "OPEN" : "CLOSED";
    }
}

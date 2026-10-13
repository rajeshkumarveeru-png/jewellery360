package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A bill. status: DRAFT (being prepared, no stock or money moved), COMPLETED, PARTIALLY_RETURNED, RETURNED, CANCELLED.
 * paymentStatus: UNPAID, PARTIAL, PAID. Amounts are frozen when the bill is completed: changing today's gold rate never
 * changes an existing bill.
 */
@Entity @Table(name="sale")
@Getter @Setter
public class Sale {
    public static final String DRAFT = "DRAFT", COMPLETED = "COMPLETED", PARTIALLY_RETURNED = "PARTIALLY_RETURNED", RETURNED = "RETURNED", CANCELLED = "CANCELLED";

    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable=false) private Long version; // null until first saved, so save() persists (not merges) new rows
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="customer_id") private Customer customer;
    @Column(nullable=false, length=80) private String invoiceNo;
    /** Idempotency key sent by the client so a retried request never creates a second bill. */
    @Column(length=64) private String clientRef;
    @Column(nullable=false) private LocalDate saleDate;
    @Column(nullable=false, length=30) private String status = COMPLETED;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal metalValue = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal wastageValue = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal makingValue = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal stoneValue = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal otherValue = BigDecimal.ZERO;
    /** Sum of the lines before discount. */
    @Column(nullable=false, precision=19, scale=3) private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal discount = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal taxableValue = BigDecimal.ZERO;
    @Column(nullable=false, length=20) private String taxMode = "GST";
    @Column(nullable=false, precision=19, scale=3) private BigDecimal taxRate = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal cgstRate = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal sgstRate = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal gst = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal roundOff = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal total = BigDecimal.ZERO;
    /** Received against this bill (receipts minus refunds, any mode incl. advance and old gold). */
    @Column(nullable=false, precision=19, scale=3) private BigDecimal paidAmount = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal returnedAmount = BigDecimal.ZERO;
    @Column(nullable=false, length=30) private String paymentStatus = "UNPAID";
    @Column(length=1000) private String notes;
    @Column(length=500) private String cancelReason;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="custom_order_id") private CustomOrder customOrder;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
    private Instant cancelledAt;

    /** What the customer still owes on this bill (negative = shop owes the customer). */
    public BigDecimal balance() { return total.subtract(returnedAmount).subtract(paidAmount); }
}

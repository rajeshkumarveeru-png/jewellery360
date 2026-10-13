package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Every rupee that moves. type: RECEIPT (customer pays a bill / repair), ADVANCE (advance received), REFUND (shop pays the
 * customer back), PAYOUT (shop pays a supplier, karigar or old-gold seller).
 * mode: CASH, UPI, CARD, BANK, CHEQUE - or, for adjustments that move no money: ADVANCE (customer advance used on a bill),
 * OLD_GOLD (old gold value used on a bill).
 */
@Entity @Table(name="payment")
@Getter @Setter
public class Payment {
    public static final String RECEIPT = "RECEIPT", ADVANCE = "ADVANCE", REFUND = "REFUND", PAYOUT = "PAYOUT";
    public static final String MODE_ADVANCE = "ADVANCE", MODE_OLD_GOLD = "OLD_GOLD", MODE_CASH = "CASH";

    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @Column(nullable=false, length=80) private String paymentNo;
    @Column(nullable=false) private LocalDate paymentDate;
    @Column(nullable=false, length=30) private String paymentType;
    @Column(nullable=false, length=30) private String paymentMode;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal amount;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="customer_id") private Customer customer;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="supplier_id") private Supplier supplier;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="karigar_id") private Karigar karigar;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="sale_id") private Sale sale;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="purchase_id") private PurchaseOrder purchase;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="advance_id") private CustomerAdvance advance;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="old_gold_id") private OldGoldTransaction oldGold;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="repair_order_id") private RepairOrder repairOrder;
    @Column(length=160) private String reference;
    @Column(length=1000) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();

    /** True when money really changed hands (not an advance / old-gold adjustment). */
    public boolean isMoney() { return !MODE_ADVANCE.equals(paymentMode) && !MODE_OLD_GOLD.equals(paymentMode); }
    /** +1 money in, -1 money out. */
    public int direction() { return REFUND.equals(paymentType) || PAYOUT.equals(paymentType) ? -1 : 1; }
}

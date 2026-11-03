package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One "pay ₹x for bill y by UPI" request and what became of it.
 * mode: UPI_QR (shop's own UPI ID, the cashier confirms), RAZORPAY_QR / RAZORPAY_LINK (Razorpay confirms).
 * status: PENDING -> PAID (payment recorded on the bill) | CANCELLED | EXPIRED | NEEDS_REVIEW (money came in but could not be put on the bill).
 */
@Entity @Table(name="upi_payment_request")
@Getter @Setter
public class UpiPaymentRequest {
    public static final String UPI_QR = "UPI_QR", RAZORPAY_QR = "RAZORPAY_QR", RAZORPAY_LINK = "RAZORPAY_LINK";
    public static final String PENDING = "PENDING", PAID = "PAID", CANCELLED = "CANCELLED", EXPIRED = "EXPIRED", NEEDS_REVIEW = "NEEDS_REVIEW";

    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="sale_id") private Sale sale;
    @Column(nullable=false, length=20) private String mode;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal amount;
    @Column(nullable=false, length=20) private String status = PENDING;
    @Column(length=600) private String upiUri;
    /** Razorpay QR id (qr_…) or payment link id (plink_…). */
    @Column(length=80) private String providerRef;
    @Column(length=600) private String qrImageUrl;
    @Column(length=300) private String shortUrl;
    @Column(length=80) private String providerPaymentId;
    @Column(length=40) private String utr;
    @Column(length=120) private String payerVpa;
    @Column(precision=19, scale=3) private BigDecimal paidAmount;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="payment_id") private Payment payment;
    @Column(length=500) private String note;
    private Instant expiresAt;
    private Instant paidAt;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

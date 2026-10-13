package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name="purchase_order")
@Getter @Setter
public class PurchaseOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable=false) private Long version; // null until first saved, so save() persists (not merges) new rows
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="supplier_id") private Supplier supplier;
    @Column(nullable=false, length=80) private String purchaseNo;
    @Column(length=80) private String supplierInvoiceNo;
    @Column(nullable=false) private LocalDate purchaseDate;
    @Column(nullable=false, length=30) private String status = "RECEIVED";
    @Column(nullable=false, precision=19, scale=3) private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal gst = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal total = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal paidAmount = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal returnedAmount = BigDecimal.ZERO;
    @Column(length=2000) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();

    public BigDecimal balance() { return total.subtract(returnedAmount).subtract(paidAmount); }
}

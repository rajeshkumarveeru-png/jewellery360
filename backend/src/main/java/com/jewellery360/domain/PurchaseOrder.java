package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="purchase_order",
uniqueConstraints={@UniqueConstraint(name="uk_purchase_company_no", columnNames="company_id,purchase_no")})
@Getter @Setter
public class PurchaseOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Version
    @Column(nullable=false)
    private Long version = 0L;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="supplier_id") private Supplier supplier;
    @Column(nullable=false,length=80) private String purchaseNo;
    @Column(nullable=false) private LocalDate purchaseDate;
    @Column(nullable=false,length=30) private String status = "DRAFT";
    @Column(nullable=false,precision=19,scale=3) private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal gst = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal total = BigDecimal.ZERO;
    @Column(length=2000) private String notes;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

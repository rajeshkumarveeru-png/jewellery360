package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="sale",
    indexes={@Index(name="idx_sale_context_date", columnList="company_id,branch_id,sale_date")},
    uniqueConstraints={@UniqueConstraint(name="uk_sale_company_invoice", columnNames="company_id,invoice_no")})
@Getter @Setter
public class Sale {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Version
    @Column(nullable=false)
    private Long version = 0L;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="customer_id") private Customer customer;
    @Column(nullable=false,length=80) private String invoiceNo;
    @Column(nullable=false) private LocalDate saleDate;
    @Column(nullable=false,length=30) private String status = "COMPLETED";
    @Column(nullable=false,precision=19,scale=3) private BigDecimal goldRate = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal discount = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal gst = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal total = BigDecimal.ZERO;
    @Column(nullable=false,length=30) private String paymentStatus = "UNPAID";
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

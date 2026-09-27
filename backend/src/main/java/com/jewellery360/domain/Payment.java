package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="payment",
uniqueConstraints={@UniqueConstraint(name="uk_payment_company_no", columnNames="company_id,payment_no")})
@Getter @Setter
public class Payment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="customer_id") private Customer customer;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="sale_id") private Sale sale;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="purchase_id") private PurchaseOrder purchase;
    @Column(nullable=false,length=80) private String paymentNo;
    @Column(nullable=false,length=30) private String paymentType;
    @Column(nullable=false,length=30) private String paymentMode;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal amount;
    @Column(nullable=false) private LocalDate paymentDate;
    @Column(length=160) private String reference;
    @Column(length=1000) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

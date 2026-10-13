package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name="expense")
@Getter @Setter
public class Expense {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @Column(nullable=false) private LocalDate expenseDate;
    @Column(nullable=false, length=60) private String category;
    @Column(nullable=false, length=180) private String title;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal amount;
    @Column(nullable=false, length=30) private String paymentMode = "CASH";
    @Column(length=160) private String reference;
    @Column(length=1000) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

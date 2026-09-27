package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="old_gold_transaction",
uniqueConstraints={@UniqueConstraint(name="uk_old_gold_company_no", columnNames="company_id,transaction_no")})
@Getter @Setter
public class OldGoldTransaction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="customer_id") private Customer customer;
    @Column(nullable=false,length=80) private String transactionNo;
    @Column(nullable=false,length=30) private String transactionType;
    @Column(nullable=false) private LocalDate transactionDate;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(nullable=false,length=30) private String status = "COMPLETED";
    @Column(length=1000) private String notes;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

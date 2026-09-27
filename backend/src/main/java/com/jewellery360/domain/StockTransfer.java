package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="stock_transfer",
uniqueConstraints={@UniqueConstraint(name="uk_transfer_company_no", columnNames="company_id,transfer_no")})
@Getter @Setter
public class StockTransfer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="from_branch_id") private Branch fromBranch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="to_branch_id") private Branch toBranch;
    @Column(nullable=false,length=80) private String transferNo;
    @Column(nullable=false) private LocalDate transferDate;
    @Column(nullable=false,length=30) private String status = "DRAFT";
    @Column(length=1000) private String notes;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

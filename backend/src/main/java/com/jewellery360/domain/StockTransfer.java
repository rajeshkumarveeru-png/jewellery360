package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name="stock_transfer")
@Getter @Setter
public class StockTransfer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="from_branch_id") private Branch fromBranch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="to_branch_id") private Branch toBranch;
    @Column(nullable=false, length=80) private String transferNo;
    @Column(nullable=false) private LocalDate transferDate;
    @Column(nullable=false, length=30) private String status = "COMPLETED";
    @Column(length=1000) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

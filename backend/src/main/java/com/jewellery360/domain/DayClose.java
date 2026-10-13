package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** End-of-day cash count for one branch. expectedCash = opening + cash in - cash out - cash expenses; difference = actual - expected. */
@Entity @Table(name="day_close")
@Getter @Setter
public class DayClose {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @Column(nullable=false) private LocalDate closeDate;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal openingCash = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal cashIn = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal cashOut = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal cashExpenses = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal expectedCash = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal actualCash = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal difference = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal upiTotal = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal cardTotal = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal bankTotal = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal salesTotal = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal creditSales = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal advancesReceived = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal refundsPaid = BigDecimal.ZERO;
    @Column(length=1000) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

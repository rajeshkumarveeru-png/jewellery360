package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Work given to a karigar. Metal moves in and out through JobMetalMovement rows; OPEN -> RECEIVED -> CLOSED (reconciled). */
@Entity @Table(name="job_card")
@Getter @Setter
public class JobCard {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="karigar_id") private Karigar karigar;
    @Column(nullable=false, length=80) private String jobNo;
    @Column(nullable=false) private LocalDate jobDate;
    private LocalDate dueDate;
    @Column(nullable=false, length=1000) private String description;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="custom_order_id") private CustomOrder customOrder;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="repair_order_id") private RepairOrder repairOrder;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="purity_id") private PurityMaster purity;
    /** Melting / making loss the karigar is allowed, as % of metal issued. */
    @Column(nullable=false, precision=19, scale=3) private BigDecimal wastageAllowedPercent = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal labourCharge = BigDecimal.ZERO;
    @Column(nullable=false, length=30) private String status = "OPEN";
    private LocalDate closedDate;
    @Column(length=1000) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** The shop's selling rate per gram for one purity on one day in one branch. Bills copy the rate, so changing it never touches old bills. */
@Entity @Table(name="gold_rate")
@Getter @Setter
public class GoldRate {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable=false) private Long version; // null until first saved, so save() persists (not merges) new rows
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="purity_id") private PurityMaster purity;
    @Column(nullable=false) private LocalDate rateDate;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal ratePerGram;
    /** MANUAL or MARKET (taken over from the market reference by a user). */
    @Column(nullable=false, length=100) private String source = "MANUAL";
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="updated_by_id") private AppUser updatedBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
    @Column(nullable=false) private Instant updatedAt = Instant.now();
}

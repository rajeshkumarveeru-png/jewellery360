package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity @Table(name="purity_master")
@Getter @Setter
public class PurityMaster {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    /** GOLD, SILVER or PLATINUM. */
    @Column(nullable=false, length=20) private String metal = "GOLD";
    @Column(nullable=false, length=50) private String name;
    @Column(length=10) private String karat;
    /** 0.9160 for 22K. */
    @Column(nullable=false, precision=8, scale=4) private BigDecimal fineness;
    @Column(length=500) private String description;
    @Column(nullable=false) private boolean active = true;
}

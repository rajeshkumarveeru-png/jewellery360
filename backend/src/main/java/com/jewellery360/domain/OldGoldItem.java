package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/** amount = netWeight x rate x (1 - deductionPercent / 100). */
@Entity @Table(name="old_gold_item")
@Getter @Setter
public class OldGoldItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="transaction_id") private OldGoldTransaction transaction;
    @Column(nullable=false, length=500) private String description;
    @Column(nullable=false, length=50) private String purity;
    @Column(nullable=false, precision=8, scale=4) private BigDecimal fineness = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal grossWeight = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal stoneWeight = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal netWeight = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal rate = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal deductionPercent = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal amount = BigDecimal.ZERO;
}

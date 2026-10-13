package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/** A bill line: a frozen copy of the piece and its price breakup at billing time. */
@Entity @Table(name="sale_item")
@Getter @Setter
public class SaleItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="sale_id") private Sale sale;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="jewellery_item_id") private JewelleryItem jewelleryItem;
    @Column(nullable=false, length=80) private String tagNo;
    @Column(nullable=false, length=200) private String description;
    @Column(length=10) private String hsnCode;
    @Column(length=20) private String huid;
    @Column(nullable=false, length=50) private String purity;
    @Column(nullable=false, precision=8, scale=4) private BigDecimal fineness = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal grossWeight = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal stoneWeight = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal netWeight = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal rate = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal metalValue = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal wastagePercent = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal wastageValue = BigDecimal.ZERO;
    @Column(nullable=false, length=20) private String makingType = "FIXED";
    @Column(nullable=false, precision=19, scale=3) private BigDecimal makingValue = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal makingCharge = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal stoneCharge = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal otherCharge = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal discount = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal taxableValue = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal gst = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal total = BigDecimal.ZERO;
}

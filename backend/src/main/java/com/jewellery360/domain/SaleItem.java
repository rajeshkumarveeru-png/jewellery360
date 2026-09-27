package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="sale_item")
@Getter @Setter
public class SaleItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="sale_id") private Sale sale;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="jewellery_item_id") private JewelleryItem jewelleryItem;
    @Column(nullable=false,length=80) private String tagNo;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal grossWeight = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal stoneWeight = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal netWeight = BigDecimal.ZERO;
    @Column(nullable=false,length=20) private String purity;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal goldRate = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal wastageValue = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal makingCharge = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal stoneCharge = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal otherCharge = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal gst = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal total = BigDecimal.ZERO;
}

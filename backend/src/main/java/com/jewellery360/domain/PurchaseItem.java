package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="purchase_item")
@Getter @Setter
public class PurchaseItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="purchase_id") private PurchaseOrder purchase;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="jewellery_item_id") private JewelleryItem jewelleryItem;
    @Column(nullable=false,length=500) private String description;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal quantity = BigDecimal.ONE;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal grossWeight = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal netWeight = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal rate = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal amount = BigDecimal.ZERO;
}

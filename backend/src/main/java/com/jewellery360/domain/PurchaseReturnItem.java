package com.jewellery360.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.math.BigDecimal;
@Entity @Table(name="purchase_return_item")
@Getter @Setter public class PurchaseReturnItem { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="return_id") private PurchaseReturn returnRecord; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="purchase_item_id") private PurchaseItem purchaseItem; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="jewellery_item_id") private JewelleryItem jewelleryItem; @Column(nullable=false,precision=19,scale=3) private BigDecimal amount=BigDecimal.ZERO; @Column(length=1000) private String reason; }

package com.jewellery360.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.math.BigDecimal;
@Entity @Table(name="stock_transfer_item", uniqueConstraints=@UniqueConstraint(name="uk_transfer_item", columnNames="transfer_id,jewellery_item_id"))
@Getter @Setter public class StockTransferItem { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="transfer_id") private StockTransfer transfer; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="jewellery_item_id") private JewelleryItem jewelleryItem; @Column(nullable=false,precision=19,scale=3) private BigDecimal quantity=BigDecimal.ONE; }

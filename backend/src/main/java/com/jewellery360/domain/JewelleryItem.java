package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="jewellery_item",
    indexes={@Index(name="idx_item_company_branch_status", columnList="company_id,branch_id,status")})
@Getter @Setter
public class JewelleryItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Version
    @Column(nullable=false)
    private Long version = 0L;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="product_id") private JewelleryProduct product;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="tag_id") private JewelleryTag tag;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="purity_id") private PurityMaster purity;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal grossWeight;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal stoneWeight = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal netWeight;
    @Column(length=60) private String huid;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal wastagePercent = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal makingCharge = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal stoneValue = BigDecimal.ZERO;
    @Column(nullable=false,length=30) private String status = "IN_STOCK";
    @Column(nullable=false) private Instant createdAt = Instant.now();
    @Column(nullable=false) private Instant updatedAt = Instant.now();
}

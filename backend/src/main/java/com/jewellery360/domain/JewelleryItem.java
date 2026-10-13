package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

/** One physical, tagged piece in stock. Its status and branch are the single source of truth for stock. */
@Entity @Table(name="jewellery_item")
@Getter @Setter
public class JewelleryItem {
    public static final String IN_STOCK = "IN_STOCK", SOLD = "SOLD", RETURNED_TO_SUPPLIER = "RETURNED_TO_SUPPLIER", REMOVED = "REMOVED";

    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable=false) private Long version; // null until first saved, so save() persists (not merges) new rows
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="product_id") private JewelleryProduct product;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="purity_id") private PurityMaster purity;
    @Column(nullable=false, length=80) private String tagNo;
    @Column(nullable=false, length=100) private String barcode;
    /** 6-character BIS hallmark unique id. Stored as given; not verified with BIS. */
    @Column(length=20) private String huid;
    /** Diamond / gem certificate number. */
    @Column(length=80) private String certificateNo;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal grossWeight;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal stoneWeight = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal netWeight;
    @Column(nullable=false) private int stoneCount = 0;
    @Column(length=500) private String stoneDetails;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal stoneValue = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal wastagePercent = BigDecimal.ZERO;
    /** FIXED (rupees per piece), PER_GRAM (rupees per gram of net weight) or PERCENT (of metal value). */
    @Column(nullable=false, length=20) private String makingType = "FIXED";
    @Column(nullable=false, precision=19, scale=3) private BigDecimal makingValue = BigDecimal.ZERO;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal otherCharge = BigDecimal.ZERO;
    /** What the piece cost the shop (for profit). */
    @Column(nullable=false, precision=19, scale=3) private BigDecimal costPrice = BigDecimal.ZERO;
    /** Fixed selling price for non-weight items (e.g. silver articles). 0 = price by weight. */
    @Column(nullable=false, precision=19, scale=3) private BigDecimal mrp = BigDecimal.ZERO;
    /** Rack / tray / box. */
    @Column(length=120) private String location;
    @Column(nullable=false, length=30) private String status = IN_STOCK;
    /** OPENING, PURCHASE, OLD_GOLD, CUSTOM_ORDER. */
    @Column(nullable=false, length=30) private String source = "OPENING";
    @Column(length=1000) private String notes;
    @Column(nullable=false) private Instant createdAt = Instant.now();
    @Column(nullable=false) private Instant updatedAt = Instant.now();
    private Instant soldAt;

    @PreUpdate void touch() { updatedAt = Instant.now(); }
}

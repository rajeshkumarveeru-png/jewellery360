package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;

/** History of a piece: OPENING, PURCHASE_IN, SALE_OUT, SALE_CANCEL_IN, SALE_RETURN_IN, PURCHASE_RETURN_OUT, TRANSFER_OUT, TRANSFER_IN, ADJUSTMENT_OUT, EDIT. */
@Entity @Table(name="stock_movement")
@Getter @Setter
public class StockMovement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="jewellery_item_id") private JewelleryItem jewelleryItem;
    @Column(nullable=false, length=40) private String movementType;
    @Column(length=50) private String referenceType;
    private Long referenceId;
    @Column(nullable=false) private LocalDate movementDate;
    @Column(length=1000) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

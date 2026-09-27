package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="stock_movement",
    indexes={@Index(name="idx_stock_movement_context_date", columnList="company_id,branch_id,movement_date")})
@Getter @Setter
public class StockMovement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="jewellery_item_id") private JewelleryItem jewelleryItem;
    @Column(nullable=false,length=40) private String movementType;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal quantity = BigDecimal.ONE;
    @Column(length=50) private String referenceType;
    private Long referenceId;
    @Column(nullable=false) private LocalDate movementDate;
    @Column(length=1000) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
}

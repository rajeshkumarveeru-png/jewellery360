package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="stock",
uniqueConstraints={@UniqueConstraint(name="uk_stock_branch_item", columnNames="branch_id,jewellery_item_id")})
@Getter @Setter
public class Stock {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Version
    @Column(nullable=false)
    private Long version = 0L;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="jewellery_item_id") private JewelleryItem jewelleryItem;
    @Column(nullable=false,length=30) private String status = "IN_STOCK";
    @Column(length=120) private String location;
    @Column(nullable=false) private Instant updatedAt = Instant.now();
}

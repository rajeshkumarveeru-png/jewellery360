package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="jewellery_product",
uniqueConstraints={@UniqueConstraint(name="uk_product_company_sku", columnNames="company_id,sku")})
@Getter @Setter
public class JewelleryProduct {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="category_id") private JewelleryCategory category;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="design_id") private JewelleryDesign design;
    @Column(nullable=false,length=180) private String name;
    @Column(nullable=false,length=80) private String sku;
    @Column(length=2000) private String description;
    @Column(nullable=false) private boolean active = true;
}

package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="jewellery_design",
uniqueConstraints={@UniqueConstraint(name="uk_design_company_code", columnNames="company_id,code")})
@Getter @Setter
public class JewelleryDesign {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="category_id") private JewelleryCategory category;
    @Column(nullable=false,length=180) private String name;
    @Column(nullable=false,length=60) private String code;
    @Column(length=2000) private String description;
    @Column(nullable=false) private boolean active = true;
}

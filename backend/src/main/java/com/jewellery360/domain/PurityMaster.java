package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="purity_master",
uniqueConstraints={@UniqueConstraint(name="uk_purity_company_name", columnNames="company_id,name")})
@Getter @Setter
public class PurityMaster {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @Column(nullable=false,length=50) private String name;
    @Column(length=10) private String karat;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal fineness;
    @Column(nullable=false) private boolean active = true;
}

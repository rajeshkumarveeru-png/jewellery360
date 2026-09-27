package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="supplier",
uniqueConstraints={@UniqueConstraint(name="uk_supplier_company_name", columnNames="company_id,name")})
@Getter @Setter
public class Supplier {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @Column(nullable=false,length=180) private String name;
    @Column(length=25) private String phone;
    @Column(length=180) private String email;
    @Column(length=600) private String address;
    @Column(length=30) private String gstin;
    @Column(nullable=false) private boolean active = true;
}

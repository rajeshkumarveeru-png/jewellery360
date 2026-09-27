package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="property",
    uniqueConstraints=@UniqueConstraint(name="uk_property_company_key", columnNames={"company_id","property_key"}))
@Getter @Setter
public class Property {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="company_id")
    private Company company;
    @Column(name="property_key", nullable=false, length=120) private String key;
    @Column(name="property_value", length=4000) private String value;
    @Column(name="property_type", nullable=false, length=20) private String type = "STRING";
    @Column(nullable=false) private boolean active = true;
}

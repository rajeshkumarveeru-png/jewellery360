package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="property")
@Getter @Setter
public class Property {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="company_id")
    private Company company;

    /** Null means this is a company-level property. */
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id")
    private AppUser user;

    @Column(name="property_key", nullable=false, length=120) private String key;
    @Column(name="property_value", length=4000) private String value;
    @Column(name="property_type", nullable=false, length=20) private String type = "STRING";
    @Column(nullable=false) private boolean active = true;
}

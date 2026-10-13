package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="jewellery_category")
@Getter @Setter
public class JewelleryCategory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @Column(nullable=false, length=120) private String name;
    @Column(nullable=false, length=40) private String code;
    @Column(nullable=false) private boolean active = true;
}

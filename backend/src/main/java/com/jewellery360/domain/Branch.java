package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="branch")
@Getter @Setter
public class Branch {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) @JoinColumn(name="company_id")
    private Company company;
    @Column(nullable=false, length=120) private String name;
    @Column(nullable=false, unique=true, length=30) private String code;
    @Column(length=20) private String phone;
    @Column(length=150) private String email;
    @Column(length=30) private String gstin;
    @Column(length=180) private String website;
    @Column(length=180) private String invoiceTitle;
    @Column(length=250) private String invoiceSubtitle;
    @Column(length=1000) private String invoiceTerms;
    @Column(length=1000) private String invoiceFooter;
    @Column(length=500) private String address;
    @Column(nullable=false) private boolean active = true;
}

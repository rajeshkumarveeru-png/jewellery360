package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="company")
@Getter @Setter
public class Company {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false, unique=true, length=150) private String name;
    @Column(unique=true, length=30) private String code;
    @Column(length=30) private String gstin;
    @Column(length=20) private String phone;
    @Column(length=150) private String email;
    @Column(length=500) private String address;
    @Column(nullable=false) private boolean active = true;
}

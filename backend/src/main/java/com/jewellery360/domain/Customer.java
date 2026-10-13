package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;

/** Customers belong to the company (every branch sees them); branch is where they were registered. */
@Entity @Table(name="customer")
@Getter @Setter
public class Customer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable=false) private Long version; // null until first saved, so save() persists (not merges) new rows
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @Column(nullable=false, length=180) private String name;
    @Column(length=25) private String phone;
    @Column(length=180) private String email;
    @Column(length=600) private String address;
    @Column(length=30) private String gstin;
    @Column(length=20) private String pan;
    private LocalDate birthday;
    private LocalDate anniversary;
    @Column(length=2000) private String notes;
    @Column(nullable=false) private boolean active = true;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Goldsmith / craftsman who makes or repairs jewellery with metal issued by the shop. */
@Entity @Table(name="karigar")
@Getter @Setter
public class Karigar {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @Column(nullable=false, length=150) private String name;
    @Column(length=25) private String phone;
    @Column(length=500) private String address;
    @Column(length=1000) private String notes;
    @Column(nullable=false) private boolean active = true;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

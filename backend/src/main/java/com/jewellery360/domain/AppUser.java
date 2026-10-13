package com.jewellery360.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="app_user")
@Getter @Setter
public class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, length=80) private String username;
    @Column(nullable=false, length=200) private String email;
    @Column(length=20) private String phone;
    @JsonIgnore @Column(nullable=false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private AppRole role;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="branch_id") private Branch branch;
    /** Pages and powers the company admin granted (comma separated keys). Empty means "the defaults of the role". */
    @Column(length=800) private String permissions;
    @Column(nullable=false) private boolean enabled = false;
    @Column(nullable=false) private boolean deleted = false;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

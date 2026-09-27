package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="user_preference")
@Getter @Setter
public class UserPreference {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional=false, fetch=FetchType.LAZY) @JoinColumn(name="user_id", unique=true)
    private AppUser user;
    @Column(nullable=false, length=40) private String uiTheme = "LUXURY_GOLD";
    @Column(nullable=false, length=30) private String density = "COMFORTABLE";
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="default_branch_id") private Branch defaultBranch;
}

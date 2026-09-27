package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="user_permission",
uniqueConstraints={@UniqueConstraint(name="uk_user_permission", columnNames="user_id,permission_id")})
@Getter @Setter
public class UserPermission {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id") private AppUser user;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="permission_id") private Permission permission;
    @Column(nullable=false) private boolean granted = true;
}

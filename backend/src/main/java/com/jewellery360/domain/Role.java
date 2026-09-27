package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="role",
uniqueConstraints={@UniqueConstraint(name="uk_role_code", columnNames="code")})
@Getter @Setter
public class Role {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false,length=40) private String code;
    @Column(nullable=false,length=120) private String name;
}

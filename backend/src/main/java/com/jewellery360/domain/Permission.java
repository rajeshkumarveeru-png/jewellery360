package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="permission",
uniqueConstraints={@UniqueConstraint(name="uk_permission_code", columnNames="code")})
@Getter @Setter
public class Permission {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false,length=80) private String code;
    @Column(nullable=false,length=160) private String name;
}

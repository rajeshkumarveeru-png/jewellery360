package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="jewellery_tag",
uniqueConstraints={@UniqueConstraint(name="uk_tag_company_no", columnNames="company_id,tag_no"),@UniqueConstraint(name="uk_tag_company_barcode", columnNames="company_id,barcode")})
@Getter @Setter
public class JewelleryTag {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @Column(nullable=false,length=80) private String tagNo;
    @Column(nullable=false,length=100) private String barcode;
    @Column(nullable=false,length=30) private String status = "AVAILABLE";
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

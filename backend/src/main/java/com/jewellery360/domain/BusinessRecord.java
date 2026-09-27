package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name="business_record",
    indexes={
        @Index(name="idx_record_company_module", columnList="company_id,module"),
        @Index(name="idx_record_branch_module", columnList="branch_id,module"),
        @Index(name="idx_record_date", columnList="record_date")
    })
@Getter @Setter
public class BusinessRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length=40) private String module;
    @Column(length=80) private String subtype;
    @Column(nullable=false, length=180) private String title;
    @Column(nullable=false, length=30) private String status = "ACTIVE";
    @Column(precision=19, scale=3, nullable=false) private BigDecimal amount = BigDecimal.ZERO;
    @Column(precision=19, scale=3, nullable=false) private BigDecimal quantity = BigDecimal.ONE;
    @Column(name="record_date", nullable=false) private LocalDate recordDate;
    @Column(length=120) private String owner;
    @Column(length=2000) private String notes;

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="company_id")
    private Company company;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="branch_id")
    private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id")
    private AppUser createdBy;

    @Lob @Column(columnDefinition="TEXT") private String payload;

    @Column(nullable=false) private Instant createdAt = Instant.now();
    @Column(nullable=false) private Instant updatedAt = Instant.now();

    @PreUpdate void touch(){ updatedAt=Instant.now(); }
}

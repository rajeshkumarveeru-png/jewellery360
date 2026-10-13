package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Every gram that goes to (ISSUE) or comes back from (RECEIPT) a karigar. fineWeight = weight x purity fineness. */
@Entity @Table(name="job_metal_movement")
@Getter @Setter
public class JobMetalMovement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="job_card_id") private JobCard jobCard;
    @Column(nullable=false, length=20) private String movementType;
    @Column(nullable=false) private LocalDate movementDate;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal weight;
    @Column(nullable=false, precision=19, scale=3) private BigDecimal fineWeight;
    @Column(length=500) private String notes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

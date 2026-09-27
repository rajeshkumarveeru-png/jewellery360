package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="gold_rate",
uniqueConstraints={@UniqueConstraint(name="uk_gold_rate_context_date", columnNames="company_id,branch_id,purity_id,rate_date")})
@Getter @Setter
public class GoldRate {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Version
    @Column(nullable=false)
    private Long version = 0L;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="purity_id") private PurityMaster purity;
    @Column(nullable=false) private LocalDate rateDate;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal ratePerGram;
    @Column(length=100) private String source;
    @Column(nullable=false) private boolean active = true;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}

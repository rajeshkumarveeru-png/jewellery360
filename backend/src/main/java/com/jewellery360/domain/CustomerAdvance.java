package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="customer_advance",
uniqueConstraints={@UniqueConstraint(name="uk_advance_company_no", columnNames="company_id,advance_no")})
@Getter @Setter
public class CustomerAdvance {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="customer_id") private Customer customer;
    @Column(nullable=false,length=80) private String advanceNo;
    @Column(nullable=false) private LocalDate advanceDate;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal amount;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal utilizedAmount = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal balanceAmount = BigDecimal.ZERO;
    @Column(nullable=false,length=30) private String status = "OPEN";
    @Column(length=1000) private String notes;
}

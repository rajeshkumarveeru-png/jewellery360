package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="repair_order",
uniqueConstraints={@UniqueConstraint(name="uk_repair_company_no", columnNames="company_id,order_no")})
@Getter @Setter
public class RepairOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id") private Branch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="customer_id") private Customer customer;
    @Column(nullable=false,length=80) private String orderNo;
    @Column(length=80) private String itemTag;
    @Column(nullable=false) private LocalDate receivedDate;
    @Column(nullable=false,length=2000) private String description;
    @Column(nullable=false,length=30) private String status = "RECEIVED";
    @Column(nullable=false,precision=19,scale=3) private BigDecimal estimate = BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal finalAmount = BigDecimal.ZERO;
    private LocalDate completedDate;
    @Column(length=1000) private String notes;
}

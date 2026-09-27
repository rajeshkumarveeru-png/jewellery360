package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name="audit_history", indexes={
    @Index(name="idx_audit_company_created", columnList="company_id,created_at"),
    @Index(name="idx_audit_entity", columnList="entity_type,entity_id")
})
@Getter @Setter
public class AuditHistory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id") private AppUser user;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="branch_id") private Branch branch;
    @Column(nullable=false,length=80) private String action;
    @Column(nullable=false,length=80) private String entityType;
    private Long entityId;
    @Column(columnDefinition="TEXT") private String beforeValue;
    @Column(columnDefinition="TEXT") private String afterValue;
}

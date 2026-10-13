package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Next number of a document series (invoice, receipt, return ...) per company and financial year. */
@Entity @Table(name="document_sequence")
@Getter @Setter
public class DocumentSequence {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) @JoinColumn(name="company_id") private Company company;
    @Column(nullable=false, length=40) private String docKey;
    @Column(nullable=false) private long nextValue = 1;
}

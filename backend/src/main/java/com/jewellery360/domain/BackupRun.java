package com.jewellery360.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** One database backup or restore. kind: MANUAL, SCHEDULED, RESTORE. status: RUNNING, SUCCESS, FAILED. */
@Entity @Table(name="backup_run")
@Getter @Setter
public class BackupRun {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, length=20) private String kind;
    @Column(nullable=false, length=20) private String status;
    @Column(length=255) private String fileName;
    @Column(nullable=false) private long fileSize = 0;
    @Column(nullable=false) private Instant startedAt = Instant.now();
    private Instant finishedAt;
    @Column(length=2000) private String message;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_id") private AppUser createdBy;
}

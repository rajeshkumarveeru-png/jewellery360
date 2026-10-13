package com.jewellery360.repository;

import com.jewellery360.domain.BackupRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BackupRunRepository extends JpaRepository<BackupRun, Long> {
    @EntityGraph(attributePaths = "createdBy")
    List<BackupRun> findTop50ByOrderByStartedAtDesc();
    Optional<BackupRun> findFirstByStatusAndKindNotOrderByStartedAtDesc(String status, String kind);
    List<BackupRun> findByStatusAndKindNotOrderByStartedAtDesc(String status, String kind);
}

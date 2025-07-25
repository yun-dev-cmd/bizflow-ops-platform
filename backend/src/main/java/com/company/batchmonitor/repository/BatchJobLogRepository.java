package com.company.batchmonitor.repository;

import com.company.batchmonitor.domain.BatchJobLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BatchJobLogRepository extends JpaRepository<BatchJobLog, Long> {
    List<BatchJobLog> findAllByOrderByStartedAtDesc();
    Optional<BatchJobLog> findFirstByJobNameOrderByStartedAtDesc(String jobName);
}

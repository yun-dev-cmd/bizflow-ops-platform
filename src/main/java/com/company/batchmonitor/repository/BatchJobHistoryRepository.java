package com.company.batchmonitor.repository;

import com.company.batchmonitor.domain.BatchJobHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BatchJobHistoryRepository extends JpaRepository<BatchJobHistory, Long> {
    List<BatchJobHistory> findTop100ByOrderByStartTimeDesc();
    List<BatchJobHistory> findByStatus(String status);
}

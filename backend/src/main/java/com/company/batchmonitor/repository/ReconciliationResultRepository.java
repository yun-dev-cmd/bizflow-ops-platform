package com.company.batchmonitor.repository;

import com.company.batchmonitor.domain.ReconciliationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, Long> {
    Optional<ReconciliationResult> findFirstBySettlementRequestIdOrderByCreatedAtDesc(Long settlementRequestId);
    List<ReconciliationResult> findAllByOrderByCreatedAtDesc();
    long countByResultType(String resultType);
}

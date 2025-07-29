package com.company.batchmonitor.repository;

import com.company.batchmonitor.domain.ExternalResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExternalResultRepository extends JpaRepository<ExternalResult, String> {
    Optional<ExternalResult> findBySettlementRequestId(Long settlementRequestId);
    void deleteBySettlementRequestId(Long settlementRequestId);
}

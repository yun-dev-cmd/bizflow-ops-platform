package com.company.batchmonitor.repository;

import com.company.batchmonitor.domain.SettlementRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface SettlementRequestRepository extends JpaRepository<SettlementRequest, Long> {
    List<SettlementRequest> findByStatus(String status);
    
    @Query("SELECT s FROM SettlementRequest s JOIN FETCH s.requester LEFT JOIN FETCH s.assignedOperator LEFT JOIN FETCH s.attachment ORDER BY s.id DESC")
    List<SettlementRequest> findAllWithRelations();

    long countByStatus(String status);
    long countByStatusIn(List<String> statuses);
}

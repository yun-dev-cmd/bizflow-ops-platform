package com.company.batchmonitor.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ReconciliationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_request_id")
    private Long settlementRequestId; // 내부 정산 요청 ID (nullable)

    @Column(nullable = false)
    private String resultType; // MATCHED, MISMATCHED, MISSING_EXTERNAL, UNKNOWN_EXTERNAL, INVALID_STATUS

    private Long internalAmount; // 내부 정산 요청 금액 (nullable)

    private Long externalAmount; // 외부 실적 금액 (nullable)

    @Column(length = 1000)
    private String reason; // 매칭 상세 사유

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

package com.company.batchmonitor.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "external_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ExternalResult {

    @Id
    @Column(name = "external_transaction_id", nullable = false)
    private String externalTransactionId; // 외부 거래 고유 ID

    @Column(name = "settlement_request_id")
    private Long settlementRequestId; // 정산 요청 ID (nullable)

    @Column(nullable = false)
    private Long externalAmount; // 외부 수신 금액

    @Column(nullable = false)
    private String externalStatus; // 외부 거래 상태 (APPROVED 등)

    @Column(nullable = false)
    private LocalDateTime receivedAt; // 수신 일시
}

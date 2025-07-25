package com.company.batchmonitor.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SettlementRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Long amount; // 정산 요청 금액

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester; // 요청 등록자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_operator_id")
    private User assignedOperator; // 담당 배정자

    @Column(nullable = false)
    private String status; // REQUESTED, ASSIGNED, APPROVED, REJECTED

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id")
    private Attachment attachment; // 증빙 파일

    @Column(nullable = false)
    private String externalSyncStatus; // PENDING, SUCCESS, ERROR (외부 연계 상태)

    @Column(nullable = false)
    private String reconciliationStatus; // UNVERIFIED, MATCHED, MISMATCHED, MISSING_EXTERNAL, UNKNOWN_EXTERNAL, INVALID_STATUS

    private Long externalAmount; // 외부 기관에서 회신된 실거래 정산 금액

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void assignOperator(User operator) {
        this.assignedOperator = operator;
        this.status = "ASSIGNED";
        this.updatedAt = LocalDateTime.now();
    }

    public void approve() {
        this.status = "APPROVED";
        this.updatedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = "REJECTED";
        this.updatedAt = LocalDateTime.now();
    }

    public void updateReconciliation(String syncStatus, String reconStatus, Long extAmount) {
        this.externalSyncStatus = syncStatus;
        this.reconciliationStatus = reconStatus;
        this.externalAmount = extAmount;
        this.updatedAt = LocalDateTime.now();
    }

    public void setAttachment(Attachment attachment) {
        this.attachment = attachment;
        this.updatedAt = LocalDateTime.now();
    }
}

package com.company.batchmonitor.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "batch_job_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BatchJobHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String jobName;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Column(nullable = false)
    private String status; // RUNNING, SUCCESS, FAILED

    @Column(length = 4000)
    private String exitMessage; // 실패 사유 및 스택트레이스 일부

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private boolean isRetried;

    private String retriedBy; // 재처리를 수행한 관리자/운영자 ID

    private LocalDateTime retriedAt;

    public void complete(String status, String exitMessage) {
        this.status = status;
        this.exitMessage = exitMessage;
        this.endTime = LocalDateTime.now();
    }

    public void markAsRetried(String operatorName) {
        this.isRetried = true;
        this.retryCount += 1;
        this.retriedBy = operatorName;
        this.retriedAt = LocalDateTime.now();
    }

    public void updateStatus(String status) {
        this.status = status;
    }
}

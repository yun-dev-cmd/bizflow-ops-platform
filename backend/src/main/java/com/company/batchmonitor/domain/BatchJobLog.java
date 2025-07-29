package com.company.batchmonitor.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "batch_job_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BatchJobLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String jobName;

    @Column(nullable = false)
    private String status; // RUNNING, SUCCESS, FAILED

    @Column(nullable = false)
    private int successCount;

    @Column(nullable = false)
    private int failCount;

    @Column(length = 2000)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private boolean isRetried;

    private String retriedBy;

    private LocalDateTime retriedAt;

    public void complete(String status, int successCount, int failCount, String errorMessage) {
        this.status = status;
        this.successCount = successCount;
        this.failCount = failCount;
        this.errorMessage = errorMessage;
        this.finishedAt = LocalDateTime.now();
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

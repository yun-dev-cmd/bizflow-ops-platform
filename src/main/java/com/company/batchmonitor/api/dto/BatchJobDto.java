package com.company.batchmonitor.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class BatchJobDto {
    private Long id;
    private String jobName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String exitMessage;
    private int retryCount;
    private boolean isRetried;
    private String retriedBy;
    private LocalDateTime retriedAt;
}

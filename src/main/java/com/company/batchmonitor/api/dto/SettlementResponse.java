package com.company.batchmonitor.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class SettlementResponse {
    private Long id;
    private String title;
    private Long amount;
    private String requesterName;
    private String assigneeName;
    private String status;
    private String originalFileName;
    private String fileUrl;
    private String externalSyncStatus;
    private String reconciliationStatus;
    private Long externalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.company.batchmonitor.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class DashboardSummaryResponse {
    private long totalRequests;
    private long pendingRequests; // REQUESTED, ASSIGNED
    private long approvedRequests; // APPROVED
    private long rejectedRequests; // REJECTED
    
    private long matchedCount;
    private long mismatchedCount;
    private long missingExternalCount;
    private long unknownExternalCount;
    private long invalidStatusCount;

    private String lastBatchStatus;
    private String lastBatchErrorMessage;
    private long retryRequiredCount; // MATCHED가 아닌 정합성 불일치/에러 건수
}

package com.company.batchmonitor.api.controller;

import com.company.batchmonitor.api.dto.DashboardSummaryResponse;
import com.company.batchmonitor.domain.BatchJobLog;
import com.company.batchmonitor.repository.BatchJobLogRepository;
import com.company.batchmonitor.repository.ReconciliationResultRepository;
import com.company.batchmonitor.repository.SettlementRequestRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@Tag(name = "Dashboard", description = "대시보드 통계 및 모니터링 API")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final SettlementRequestRepository settlementRequestRepository;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final BatchJobLogRepository batchJobLogRepository;

    @Operation(summary = "대시보드 요약 정보 조회", description = "정산 요청 및 배치 정합성 통계 요약 데이터를 반환합니다.")
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        long totalRequests = settlementRequestRepository.count();
        long pendingRequests = settlementRequestRepository.countByStatusIn(List.of("REQUESTED", "ASSIGNED"));
        long approvedRequests = settlementRequestRepository.countByStatus("APPROVED");
        long rejectedRequests = settlementRequestRepository.countByStatus("REJECTED");

        long matched = reconciliationResultRepository.countByResultType("MATCHED");
        long mismatched = reconciliationResultRepository.countByResultType("MISMATCHED");
        long missingExternal = reconciliationResultRepository.countByResultType("MISSING_EXTERNAL");
        long unknownExternal = reconciliationResultRepository.countByResultType("UNKNOWN_EXTERNAL");
        long invalidStatus = reconciliationResultRepository.countByResultType("INVALID_STATUS");

        // 최근 배치 작업 이력 확인
        Optional<BatchJobLog> lastLogOpt = batchJobLogRepository.findFirstByJobNameOrderByStartedAtDesc("settlementVerificationJob");
        String lastBatchStatus = "-";
        String lastBatchErrorMessage = "-";
        
        if (lastLogOpt.isPresent()) {
            BatchJobLog log = lastLogOpt.get();
            lastBatchStatus = log.getStatus();
            lastBatchErrorMessage = log.getErrorMessage() != null ? log.getErrorMessage() : "정상 처리";
        }

        // 재처리 대상 건수: MATCHED가 아닌 불일치/에러 건의 합계
        long retryRequired = mismatched + missingExternal + unknownExternal + invalidStatus;

        DashboardSummaryResponse response = DashboardSummaryResponse.builder()
                .totalRequests(totalRequests)
                .pendingRequests(pendingRequests)
                .approvedRequests(approvedRequests)
                .rejectedRequests(rejectedRequests)
                .matchedCount(matched)
                .mismatchedCount(mismatched)
                .missingExternalCount(missingExternal)
                .unknownExternalCount(unknownExternal)
                .invalidStatusCount(invalidStatus)
                .lastBatchStatus(lastBatchStatus)
                .lastBatchErrorMessage(lastBatchErrorMessage)
                .retryRequiredCount(retryRequired)
                .build();

        return ResponseEntity.ok(response);
    }
}

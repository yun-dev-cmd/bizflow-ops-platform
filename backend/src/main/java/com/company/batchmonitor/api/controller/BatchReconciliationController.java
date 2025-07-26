package com.company.batchmonitor.api.controller;

import com.company.batchmonitor.api.dto.BatchRetryRequest;
import com.company.batchmonitor.api.service.BatchMonitoringService;
import com.company.batchmonitor.domain.BatchJobLog;
import com.company.batchmonitor.domain.ReconciliationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Batch Reconciliation", description = "정합성 검증 배치 제어 및 결과 조회 API")
@RestController
@RequiredArgsConstructor
public class BatchReconciliationController {

    private final BatchMonitoringService batchMonitoringService;

    @Operation(summary = "정합성 검증 배치 가동", description = "내부 정산 요청과 외부 실적 데이터 대조 정합성 배치를 즉시 실행합니다. (ADMIN/OPERATOR 필요)")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PostMapping("/api/batches/reconciliation/run")
    public ResponseEntity<String> runReconciliation(
            @RequestParam(defaultValue = "false") boolean mockFailure,
            @AuthenticationPrincipal UserDetails userDetails) {
        batchMonitoringService.triggerJob("settlementVerificationJob", userDetails.getUsername(), mockFailure);
        return ResponseEntity.ok("정산 정합성 검증 배치가 실행되었습니다.");
    }

    @Operation(summary = "실패 배치 재처리", description = "실패한 정합성 검증 배치를 재수행합니다. (ADMIN 필요)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/batches/reconciliation/retry")
    public ResponseEntity<String> retryReconciliation(
            @RequestBody BatchRetryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        batchMonitoringService.retryFailedJob(request.getLogId(), userDetails.getUsername());
        return ResponseEntity.ok("배치 재처리 실행이 요청되었습니다.");
    }

    @Operation(summary = "배치 실행 로그 조회", description = "배치 작업 수행 로그 목록을 전체 조회합니다. (ADMIN/OPERATOR 필요)")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/api/batches/logs")
    public ResponseEntity<List<BatchJobLog>> getBatchLogs() {
        List<BatchJobLog> logs = batchMonitoringService.getBatchLogs();
        return ResponseEntity.ok(logs);
    }

    @Operation(summary = "정합성 결과 조회", description = "정합성 검증 대조 상세 결과 목록을 전체 조회합니다. (ADMIN/OPERATOR 필요)")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/api/reconciliation-results")
    public ResponseEntity<List<ReconciliationResult>> getReconciliationResults() {
        List<ReconciliationResult> results = batchMonitoringService.getReconciliationResults();
        return ResponseEntity.ok(results);
    }
}

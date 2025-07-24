package com.company.batchmonitor.api.controller;

import com.company.batchmonitor.api.dto.BatchExecuteRequest;
import com.company.batchmonitor.api.dto.BatchJobDto;
import com.company.batchmonitor.api.service.BatchMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Batch Monitoring", description = "배치 실행 이력 조회 및 재기동 제어 API")
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchMonitoringController {

    private final BatchMonitoringService batchMonitoringService;

    @Operation(summary = "배치 실행 이력 조회", description = "최근 100건의 배치 실행 이력을 조회합니다. (권한별 조회 가능: USER, OPERATOR, ADMIN)")
    @GetMapping("/histories")
    public ResponseEntity<List<BatchJobDto>> getJobHistories() {
        List<BatchJobDto> histories = batchMonitoringService.getJobHistories();
        return ResponseEntity.ok(histories);
    }

    @Operation(summary = "배치 수동 기동", description = "특정 배치 작업을 수동으로 즉시 구동합니다. (OPERATOR, ADMIN 권한 필요)")
    @PostMapping("/trigger/{jobName}")
    public ResponseEntity<String> triggerJob(
            @PathVariable String jobName,
            @AuthenticationPrincipal UserDetails userDetails) {
        batchMonitoringService.triggerJob(jobName, userDetails.getUsername());
        return ResponseEntity.ok(jobName + " 배치가 성공적으로 요청되었습니다.");
    }

    @Operation(summary = "실패 배치 재처리(재기동)", description = "실패한 배치 작업을 강제로 다시 기동하여 재처리 프로세스를 수행합니다. (OPERATOR, ADMIN 권한 필요)")
    @PostMapping("/retry")
    public ResponseEntity<String> retryFailedJob(
            @RequestBody BatchExecuteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        batchMonitoringService.retryFailedJob(request.getHistoryId(), userDetails.getUsername());
        return ResponseEntity.ok("배치 재처리 실행이 시작되었습니다.");
    }
}

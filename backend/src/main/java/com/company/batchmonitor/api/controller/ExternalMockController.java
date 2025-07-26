package com.company.batchmonitor.api.controller;

import com.company.batchmonitor.api.dto.ExternalMockRequest;
import com.company.batchmonitor.domain.ExternalResult;
import com.company.batchmonitor.repository.ExternalResultRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "External Mock Data Management", description = "외부 연계 실적 Mock 데이터 생성 및 조회 API")
@RestController
@RequestMapping("/api/external-results")
@RequiredArgsConstructor
public class ExternalMockController {

    private final ExternalResultRepository externalResultRepository;

    @Operation(summary = "외부 연계 Mock 데이터 생성", description = "대조 검증용 외부 실적 데이터를 Mock으로 강제 주입합니다.")
    @PostMapping("/mock")
    public ResponseEntity<ExternalResult> createMockResult(@Valid @RequestBody ExternalMockRequest request) {
        ExternalResult externalResult = ExternalResult.builder()
                .externalTransactionId(request.getExternalTransactionId())
                .settlementRequestId(request.getSettlementRequestId())
                .externalAmount(request.getExternalAmount())
                .externalStatus(request.getExternalStatus())
                .receivedAt(LocalDateTime.now())
                .build();

        ExternalResult saved = externalResultRepository.save(externalResult);
        return ResponseEntity.ok(saved);
    }

    @Operation(summary = "외부 실적 전체 조회", description = "수신된 외부 연계 실적 목록을 전체 조회합니다. (ADMIN/OPERATOR 필요)")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping
    public ResponseEntity<List<ExternalResult>> getExternalResults() {
        List<ExternalResult> results = externalResultRepository.findAll();
        return ResponseEntity.ok(results);
    }
}

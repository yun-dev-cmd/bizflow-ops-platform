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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "External Mock Data Management", description = "External reconciliation mock data APIs")
@RestController
@RequestMapping("/api/external-results")
@RequiredArgsConstructor
public class ExternalMockController {

    private final ExternalResultRepository externalResultRepository;

    @Operation(summary = "Create external mock result")
    @PostMapping("/mock")
    @Transactional
    public ResponseEntity<ExternalResult> createMockResult(@Valid @RequestBody ExternalMockRequest request) {
        if (request.getSettlementRequestId() != null) {
            externalResultRepository.deleteBySettlementRequestId(request.getSettlementRequestId());
        }

        ExternalResult externalResult = ExternalResult.builder()
                .externalTransactionId(request.getExternalTransactionId())
                .settlementRequestId(request.getSettlementRequestId())
                .externalAmount(request.getExternalAmount())
                .externalStatus(request.getExternalStatus())
                .receivedAt(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(externalResultRepository.save(externalResult));
    }

    @Operation(summary = "List external results")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping
    public ResponseEntity<List<ExternalResult>> getExternalResults() {
        return ResponseEntity.ok(externalResultRepository.findAll());
    }
}

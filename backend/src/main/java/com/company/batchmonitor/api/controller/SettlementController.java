package com.company.batchmonitor.api.controller;

import com.company.batchmonitor.api.dto.SettlementAssignRequest;
import com.company.batchmonitor.api.dto.SettlementCreateRequest;
import com.company.batchmonitor.api.dto.SettlementResponse;
import com.company.batchmonitor.api.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Settlement Management", description = "업무 요청 및 정산 증빙 통합 관리 API")
@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @Operation(summary = "정산 요청 등록", description = "신규 업무 요청 및 정산 증빙 내역을 등록합니다.")
    @PostMapping
    public ResponseEntity<SettlementResponse> createRequest(
            @Valid @RequestBody SettlementCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        SettlementResponse response = settlementService.createRequest(request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "정산 담당자 배정", description = "특정 정산 요청 건에 대해 처리 담당 운영자를 배정합니다. (ADMIN/OPERATOR 필요)")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PostMapping("/{id}/assign")
    public ResponseEntity<SettlementResponse> assignOperator(
            @PathVariable Long id,
            @Valid @RequestBody SettlementAssignRequest request) {
        SettlementResponse response = settlementService.assignOperator(id, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "정산 승인", description = "정산 요청 및 증빙파일을 확인 후 최종 승인 처리합니다. (ADMIN/OPERATOR 필요)")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<SettlementResponse> approveRequest(@PathVariable Long id) {
        SettlementResponse response = settlementService.approveRequest(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "정산 반려", description = "부적합한 정산 요청 및 증빙 건에 대해 반려 처리합니다. (ADMIN/OPERATOR 필요)")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<SettlementResponse> rejectRequest(@PathVariable Long id) {
        SettlementResponse response = settlementService.rejectRequest(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "정산 목록 전체 조회", description = "등록된 전체 정산 요청 내역을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<SettlementResponse>> getAllRequests() {
        List<SettlementResponse> list = settlementService.getAllRequests();
        return ResponseEntity.ok(list);
    }
}

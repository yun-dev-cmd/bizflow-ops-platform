package com.company.batchmonitor.api.controller;

import com.company.batchmonitor.api.dto.SettlementAssignRequest;
import com.company.batchmonitor.api.dto.SettlementCreateRequest;
import com.company.batchmonitor.api.dto.SettlementResponse;
import com.company.batchmonitor.api.service.FileService;
import com.company.batchmonitor.api.service.SettlementService;
import com.company.batchmonitor.domain.Attachment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "Settlement Management", description = "정산/업무 요청 및 증빙 연계 관리 API")
@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final FileService fileService;

    @Operation(summary = "정산 요청 등록", description = "신규 업무 요청 및 정산 내역을 등록합니다. (USER, OPERATOR, ADMIN)")
    @PostMapping
    public ResponseEntity<SettlementResponse> createRequest(
            @Valid @RequestBody SettlementCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        SettlementResponse response = settlementService.createRequest(request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "정산 요청 목록 조회", description = "사용자 권한에 따른 정산 요청 목록을 조회합니다. 일반 사용자는 본인 건만 조회됩니다.")
    @GetMapping
    public ResponseEntity<List<SettlementResponse>> getRequests(@AuthenticationPrincipal UserDetails userDetails) {
        List<SettlementResponse> list = settlementService.getRequests(userDetails.getUsername());
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "정산 요청 단건 상세 조회", description = "정산 요청 상세 내역을 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<SettlementResponse> getRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        SettlementResponse response = settlementService.getRequest(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "정산 담당자 배정", description = "특정 정산 요청 건에 대해 처리 담당 운영자를 배정합니다. (ADMIN/OPERATOR 필요)")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PatchMapping("/{id}/assign")
    public ResponseEntity<SettlementResponse> assignOperator(
            @PathVariable Long id,
            @Valid @RequestBody SettlementAssignRequest request) {
        SettlementResponse response = settlementService.assignOperator(id, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "정산 승인", description = "정산 요청 및 증빙파일을 확인 후 최종 승인 처리합니다. (ADMIN/OPERATOR 필요)")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PatchMapping("/{id}/approve")
    public ResponseEntity<SettlementResponse> approveRequest(@PathVariable Long id) {
        SettlementResponse response = settlementService.approveRequest(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "정산 반려", description = "부적합한 정산 요청 및 증빙 건에 대해 반려 처리합니다. (ADMIN/OPERATOR 필요)")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PatchMapping("/{id}/reject")
    public ResponseEntity<SettlementResponse> rejectRequest(@PathVariable Long id) {
        SettlementResponse response = settlementService.rejectRequest(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "증빙파일 첨부 업로드", description = "해당 정산 요청 건에 증빙파일을 업로드하여 첨부합니다. (USER, OPERATOR, ADMIN)")
    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Attachment> uploadAttachment(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        Attachment attachment = settlementService.addAttachment(id, file, userDetails.getUsername());
        return ResponseEntity.ok(attachment);
    }

    @Operation(summary = "증빙파일 메타데이터 조회", description = "정산 요청에 첨부된 파일 메타데이터를 조회합니다. (USER, OPERATOR, ADMIN)")
    @GetMapping("/{id}/attachments")
    public ResponseEntity<Attachment> getAttachmentMetadata(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Attachment attachment = settlementService.getAttachment(id, userDetails.getUsername());
        return ResponseEntity.ok(attachment);
    }

    @Operation(summary = "증빙파일 다운로드", description = "첨부된 증빙파일을 다운로드합니다. (USER, OPERATOR, ADMIN)")
    @GetMapping("/{id}/attachments/download")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        Attachment metadata = settlementService.getAttachment(id, userDetails.getUsername());
        byte[] fileData = fileService.downloadFile(metadata.getId());

        String encodedFileName = URLEncoder.encode(metadata.getOriginalFileName(), StandardCharsets.UTF_8.toString())
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                .body(fileData);
    }
}

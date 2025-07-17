package com.company.batchmonitor.api.service.impl;

import com.company.batchmonitor.api.dto.SettlementAssignRequest;
import com.company.batchmonitor.api.dto.SettlementCreateRequest;
import com.company.batchmonitor.api.dto.SettlementResponse;
import com.company.batchmonitor.api.service.SettlementService;
import com.company.batchmonitor.domain.Attachment;
import com.company.batchmonitor.domain.SettlementRequest;
import com.company.batchmonitor.domain.User;
import com.company.batchmonitor.repository.AttachmentRepository;
import com.company.batchmonitor.repository.SettlementRequestRepository;
import com.company.batchmonitor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementRequestRepository settlementRequestRepository;
    private final UserRepository userRepository;
    private final AttachmentRepository attachmentRepository;

    @Override
    @Transactional
    public SettlementResponse createRequest(SettlementCreateRequest request, String requesterUsername) {
        User requester = userRepository.findByUsername(requesterUsername)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 요청 사용자입니다."));

        Attachment attachment = null;
        if (request.getAttachmentId() != null) {
            attachment = attachmentRepository.findById(request.getAttachmentId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 증빙 파일 ID입니다."));
        }

        SettlementRequest settlementRequest = SettlementRequest.builder()
                .title(request.getTitle())
                .amount(request.getAmount())
                .requester(requester)
                .status("REQUESTED")
                .attachment(attachment)
                .externalSyncStatus("PENDING")
                .reconciliationStatus("UNVERIFIED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        SettlementRequest saved = settlementRequestRepository.save(settlementRequest);
        return convertToResponse(saved);
    }

    @Override
    @Transactional
    public SettlementResponse assignOperator(Long id, SettlementAssignRequest request) {
        SettlementRequest settlement = settlementRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 정산 요청 건입니다."));

        User assignee = userRepository.findByUsername(request.getAssigneeUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배정 대상 운영자입니다."));

        settlement.assignOperator(assignee);
        return convertToResponse(settlementRequestRepository.save(settlement));
    }

    @Override
    @Transactional
    public SettlementResponse approveRequest(Long id) {
        SettlementRequest settlement = settlementRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 정산 요청 건입니다."));

        settlement.approve();
        return convertToResponse(settlementRequestRepository.save(settlement));
    }

    @Override
    @Transactional
    public SettlementResponse rejectRequest(Long id) {
        SettlementRequest settlement = settlementRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 정산 요청 건입니다."));

        settlement.reject();
        return convertToResponse(settlementRequestRepository.save(settlement));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettlementResponse> getAllRequests() {
        return settlementRequestRepository.findAllWithRelations().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private SettlementResponse convertToResponse(SettlementRequest entity) {
        return SettlementResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .amount(entity.getAmount())
                .requesterName(entity.getRequester().getName())
                .assigneeName(entity.getAssignee() != null ? entity.getAssignee().getName() : "-")
                .status(entity.getStatus())
                .originalFileName(entity.getAttachment() != null ? entity.getAttachment().getOriginalFileName() : "-")
                .fileUrl(entity.getAttachment() != null ? "/api/files/download/" + entity.getAttachment().getId() : "#")
                .externalSyncStatus(entity.getExternalSyncStatus())
                .reconciliationStatus(entity.getReconciliationStatus())
                .externalAmount(entity.getExternalAmount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

package com.company.batchmonitor.api.service.impl;

import com.company.batchmonitor.api.dto.SettlementAssignRequest;
import com.company.batchmonitor.api.dto.SettlementCreateRequest;
import com.company.batchmonitor.api.dto.SettlementResponse;
import com.company.batchmonitor.api.service.FileService;
import com.company.batchmonitor.api.service.SettlementService;
import com.company.batchmonitor.domain.Attachment;
import com.company.batchmonitor.domain.Role;
import com.company.batchmonitor.domain.SettlementRequest;
import com.company.batchmonitor.domain.User;
import com.company.batchmonitor.repository.AttachmentRepository;
import com.company.batchmonitor.repository.SettlementRequestRepository;
import com.company.batchmonitor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementRequestRepository settlementRequestRepository;
    private final UserRepository userRepository;
    private final AttachmentRepository attachmentRepository;
    private final FileService fileService;

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

        if (assignee.getRole() != Role.ROLE_OPERATOR && assignee.getRole() != Role.ROLE_ADMIN) {
            throw new IllegalArgumentException("운영자 또는 관리자 권한을 가진 사용자만 담당자로 지정될 수 있습니다.");
        }

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
    public List<SettlementResponse> getRequests(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        List<SettlementRequest> list;
        if (user.getRole() == Role.ROLE_USER) {
            // 일반 사용자는 본인 요청 건만 조회 가능
            list = settlementRequestRepository.findAllWithRelations().stream()
                    .filter(s -> s.getRequester().getUsername().equals(username))
                    .collect(Collectors.toList());
        } else {
            // 운영자 및 관리자는 전체 조회 가능
            list = settlementRequestRepository.findAllWithRelations();
        }

        return list.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementResponse getRequest(Long id, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        SettlementRequest settlement = settlementRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 정산 요청 건입니다."));

        if (user.getRole() == Role.ROLE_USER && !settlement.getRequester().getUsername().equals(username)) {
            throw new SecurityException("본인의 정산 요청 건만 상세 조회할 수 있습니다.");
        }

        return convertToResponse(settlement);
    }

    @Override
    @Transactional
    public Attachment addAttachment(Long id, MultipartFile file, String username) throws IOException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        SettlementRequest settlement = settlementRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 정산 요청 건입니다."));

        if (user.getRole() == Role.ROLE_USER && !settlement.getRequester().getUsername().equals(username)) {
            throw new SecurityException("본인의 정산 요청 건에만 증빙파일을 첨부할 수 있습니다.");
        }

        // 파일 업로드 수행 (settlementRequestId 전달)
        Attachment attachment = fileService.uploadFile(file, username, id);

        // 정산 요청에 첨부파일 연결
        settlement.setAttachment(attachment);
        settlementRequestRepository.save(settlement);

        return attachment;
    }

    @Override
    @Transactional(readOnly = true)
    public Attachment getAttachment(Long id, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        SettlementRequest settlement = settlementRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 정산 요청 건입니다."));

        if (user.getRole() == Role.ROLE_USER && !settlement.getRequester().getUsername().equals(username)) {
            throw new SecurityException("본인의 정산 요청 건의 첨부파일만 조회할 수 있습니다.");
        }

        if (settlement.getAttachment() == null) {
            throw new IllegalArgumentException("해당 정산 요청 건에 등록된 첨부파일이 없습니다.");
        }

        return settlement.getAttachment();
    }

    private SettlementResponse convertToResponse(SettlementRequest entity) {
        return SettlementResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .amount(entity.getAmount())
                .requesterName(entity.getRequester().getName())
                .assigneeName(entity.getAssignedOperator() != null ? entity.getAssignedOperator().getName() : "-")
                .status(entity.getStatus())
                .originalFileName(entity.getAttachment() != null ? entity.getAttachment().getOriginalFileName() : "-")
                .fileUrl(entity.getAttachment() != null ? "/api/settlements/" + entity.getId() + "/attachments/download" : "#")
                .externalSyncStatus(entity.getExternalSyncStatus())
                .reconciliationStatus(entity.getReconciliationStatus())
                .externalAmount(entity.getExternalAmount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

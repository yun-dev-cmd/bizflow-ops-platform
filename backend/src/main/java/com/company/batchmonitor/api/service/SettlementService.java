package com.company.batchmonitor.api.service;

import com.company.batchmonitor.api.dto.SettlementAssignRequest;
import com.company.batchmonitor.api.dto.SettlementCreateRequest;
import com.company.batchmonitor.api.dto.SettlementResponse;
import com.company.batchmonitor.domain.Attachment;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

public interface SettlementService {
    SettlementResponse createRequest(SettlementCreateRequest request, String requesterUsername);
    SettlementResponse assignOperator(Long id, SettlementAssignRequest request);
    SettlementResponse approveRequest(Long id);
    SettlementResponse rejectRequest(Long id);
    List<SettlementResponse> getRequests(String username);
    SettlementResponse getRequest(Long id, String username);
    Attachment addAttachment(Long id, MultipartFile file, String username) throws IOException;
    Attachment getAttachment(Long id, String username);
}

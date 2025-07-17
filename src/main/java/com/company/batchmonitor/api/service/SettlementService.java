package com.company.batchmonitor.api.service;

import com.company.batchmonitor.api.dto.SettlementAssignRequest;
import com.company.batchmonitor.api.dto.SettlementCreateRequest;
import com.company.batchmonitor.api.dto.SettlementResponse;
import java.util.List;

public interface SettlementService {
    SettlementResponse createRequest(SettlementCreateRequest request, String requesterUsername);
    SettlementResponse assignOperator(Long id, SettlementAssignRequest request);
    SettlementResponse approveRequest(Long id);
    SettlementResponse rejectRequest(Long id);
    List<SettlementResponse> getAllRequests();
}

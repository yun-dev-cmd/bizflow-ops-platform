package com.company.batchmonitor.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SettlementAssignRequest {
    @NotBlank(message = "담당자 사원번호(username)는 필수입니다.")
    private String assigneeUsername;
}

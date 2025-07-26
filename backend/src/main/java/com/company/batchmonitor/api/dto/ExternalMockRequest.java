package com.company.batchmonitor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExternalMockRequest {
    @NotBlank(message = "외부 거래 ID는 필수입니다.")
    private String externalTransactionId;

    private Long settlementRequestId; // Nullable

    @NotNull(message = "외부 실적 금액은 필수입니다.")
    private Long externalAmount;

    @NotBlank(message = "외부 상태값은 필수입니다.")
    private String externalStatus;
}

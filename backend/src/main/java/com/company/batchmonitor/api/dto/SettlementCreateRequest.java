package com.company.batchmonitor.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SettlementCreateRequest {
    @NotBlank(message = "정산 요청 제목은 필수입니다.")
    private String title;

    @NotNull(message = "정산 요청 금액은 필수입니다.")
    @Min(value = 1, message = "금액은 1원 이상이어야 합니다.")
    private Long amount;

    private Long attachmentId; // 증빙 파일 ID (선택사항)
}

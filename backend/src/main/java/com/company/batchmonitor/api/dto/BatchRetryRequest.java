package com.company.batchmonitor.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BatchRetryRequest {
    private Long logId; // 재처리할 대상 배치 로그 ID
}

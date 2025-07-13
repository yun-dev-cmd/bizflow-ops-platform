package com.company.batchmonitor.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BatchExecuteRequest {
    private Long historyId; // 재처리할 대상 이력 ID
}

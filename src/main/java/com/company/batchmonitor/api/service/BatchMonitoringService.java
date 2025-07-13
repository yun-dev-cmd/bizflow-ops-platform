package com.company.batchmonitor.api.service;

import com.company.batchmonitor.api.dto.BatchJobDto;
import java.util.List;

public interface BatchMonitoringService {
    List<BatchJobDto> getJobHistories();
    void triggerJob(String jobName, String operatorName);
    void retryFailedJob(Long historyId, String operatorName);
}

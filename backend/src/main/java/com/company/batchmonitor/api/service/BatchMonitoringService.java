package com.company.batchmonitor.api.service;

import com.company.batchmonitor.domain.BatchJobLog;
import com.company.batchmonitor.domain.ReconciliationResult;
import java.util.List;

public interface BatchMonitoringService {
    void triggerJob(String jobName, String operatorName, boolean mockFailure);
    void retryFailedJob(Long logId, String operatorName);
    List<BatchJobLog> getBatchLogs();
    List<ReconciliationResult> getReconciliationResults();
}

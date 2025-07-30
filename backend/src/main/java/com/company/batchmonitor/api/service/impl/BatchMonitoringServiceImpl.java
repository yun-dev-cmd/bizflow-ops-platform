package com.company.batchmonitor.api.service.impl;

import com.company.batchmonitor.api.service.BatchMonitoringService;
import com.company.batchmonitor.domain.BatchJobLog;
import com.company.batchmonitor.domain.ReconciliationResult;
import com.company.batchmonitor.repository.BatchJobLogRepository;
import com.company.batchmonitor.repository.ReconciliationResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchMonitoringServiceImpl implements BatchMonitoringService {

    private final BatchJobLogRepository batchJobLogRepository;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext;

    @Override
    @Transactional(readOnly = true)
    public List<BatchJobLog> getBatchLogs() {
        return batchJobLogRepository.findAllByOrderByStartedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReconciliationResult> getReconciliationResults() {
        return reconciliationResultRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public void triggerJob(String jobName, String operatorName, boolean mockFailure) {
        log.info("Batch manual trigger started: job={}, operator={}, mockFailure={}", jobName, operatorName, mockFailure);
        
        // 1. 이력 생성 (RUNNING)
        BatchJobLog jobLog = BatchJobLog.builder()
                .jobName(jobName)
                .startedAt(LocalDateTime.now())
                .status("RUNNING")
                .successCount(0)
                .failCount(0)
                .retryCount(0)
                .isRetried(false)
                .build();
        batchJobLogRepository.save(jobLog);

        // 2. 비동기/동기 배치 구동 (Spring Batch Job Bean 조회)
        try {
            Job job = applicationContext.getBean(jobName, Job.class);
            
            // 매 실행 시 새로운 파라미터를 인가하여 실행 중복 회피
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("datetime", LocalDateTime.now().toString())
                    .addString("triggeredBy", operatorName)
                    .addString("mockFailure", String.valueOf(mockFailure))
                    .addLong("logId", jobLog.getId()) // 배치 내부에서 이력을 추적할 수 있도록 전달
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(job, jobParameters);
            
            // 3. 실행 완료 상태 반영 (리스너가 DB에 완료 처리를 하지만, 예외 발생 등으로 리스너가 누락될 때를 위한 이중 잠금)
            BatchJobLog loadedLog = batchJobLogRepository.findById(jobLog.getId()).orElse(jobLog);
            if ("RUNNING".equals(loadedLog.getStatus())) {
                if (execution.getStatus() == BatchStatus.COMPLETED) {
                    loadedLog.complete("SUCCESS", 1, 0, "Execution Completed.");
                } else {
                    loadedLog.complete("FAILED", 0, 1, execution.getAllFailureExceptions().stream()
                            .map(Throwable::getMessage)
                            .collect(Collectors.joining(", ")));
                }
                batchJobLogRepository.save(loadedLog);
            }
        } catch (Exception e) {
            log.error("Failed to run batch job: {}", jobName, e);
            BatchJobLog loadedLog = batchJobLogRepository.findById(jobLog.getId()).orElse(jobLog);
            loadedLog.complete("FAILED", 0, 1, "Exception: " + e.getMessage());
            batchJobLogRepository.save(loadedLog);
            throw new RuntimeException("배치 실행 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @Override
    public void retryFailedJob(Long logId, String operatorName) {
        log.info("Batch manual retry started: logId={}, operator={}", logId, operatorName);

        BatchJobLog failedLog = batchJobLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배치 실행 이력입니다."));

        if (!"FAILED".equals(failedLog.getStatus())) {
            throw new IllegalStateException("실패 상태의 배치만 재처리할 수 있습니다.");
        }

        // 재처리 표시
        failedLog.markAsRetried(operatorName);
        batchJobLogRepository.save(failedLog);

        // 기존 배치를 신규 파라미터로 다시 기동
        triggerJob(failedLog.getJobName(), operatorName, false);
    }
}

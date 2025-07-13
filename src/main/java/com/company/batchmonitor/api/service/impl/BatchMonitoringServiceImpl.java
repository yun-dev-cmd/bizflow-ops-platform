package com.company.batchmonitor.api.service.impl;

import com.company.batchmonitor.api.dto.BatchJobDto;
import com.company.batchmonitor.api.service.BatchMonitoringService;
import com.company.batchmonitor.domain.BatchJobHistory;
import com.company.batchmonitor.repository.BatchJobHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
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

    private final BatchJobHistoryRepository batchJobHistoryRepository;
    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext;

    @Override
    @Transactional(readOnly = true)
    public List<BatchJobDto> getJobHistories() {
        return batchJobHistoryRepository.findTop100ByOrderByStartTimeDesc().stream()
                .map(history -> BatchJobDto.builder()
                        .id(history.getId())
                        .jobName(history.getJobName())
                        .startTime(history.getStartTime())
                        .endTime(history.getEndTime())
                        .status(history.getStatus())
                        .exitMessage(history.getExitMessage())
                        .retryCount(history.getRetryCount())
                        .isRetried(history.isRetried())
                        .retriedBy(history.getRetriedBy())
                        .retriedAt(history.getRetriedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void triggerJob(String jobName, String operatorName) {
        log.info("Batch manual trigger started: job={}, operator={}", jobName, operatorName);
        
        // 1. 이력 생성 (RUNNING)
        BatchJobHistory history = BatchJobHistory.builder()
                .jobName(jobName)
                .startTime(LocalDateTime.now())
                .status("RUNNING")
                .retryCount(0)
                .isRetried(false)
                .build();
        batchJobHistoryRepository.save(history);

        // 2. 비동기/동기 배치 구동 (Spring Batch Job Bean 조회)
        try {
            Job job = applicationContext.getBean(jobName, Job.class);
            
            // 매 실행 시 새로운 파라미터를 인가하여 실행 중복 회피
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("datetime", LocalDateTime.now().toString())
                    .addString("triggeredBy", operatorName)
                    .addLong("historyId", history.getId()) // 배치 내부에서 이력을 추적할 수 있도록 전달
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(job, jobParameters);
            
            // 3. 실행 완료 상태 반영
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                history.complete("SUCCESS", "Manual Execution Completed.");
            } else {
                history.complete("FAILED", execution.getAllFailureExceptions().stream()
                        .map(Throwable::getMessage)
                        .collect(Collectors.joining(", ")));
            }
        } catch (Exception e) {
            log.error("Failed to run batch job: {}", jobName, e);
            history.complete("FAILED", "Exception occurred: " + e.getMessage());
            throw new RuntimeException("배치 실행 중 오류가 발생했습니다: " + e.getMessage());
        } finally {
            batchJobHistoryRepository.save(history);
        }
    }

    @Override
    @Transactional
    public void retryFailedJob(Long historyId, String operatorName) {
        log.info("Batch manual retry started: historyId={}, operator={}", historyId, operatorName);

        BatchJobHistory failedHistory = batchJobHistoryRepository.findById(historyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배치 실행 이력입니다."));

        if (!"FAILED".equals(failedHistory.getStatus())) {
            throw new IllegalStateException("실패 상태의 배치만 재처리할 수 있습니다.");
        }

        // 재처리 표시
        failedHistory.markAsRetried(operatorName);
        batchJobHistoryRepository.save(failedHistory);

        // 기존 배치를 신규 파라미터로 다시 기동
        triggerJob(failedHistory.getJobName(), operatorName);
    }
}

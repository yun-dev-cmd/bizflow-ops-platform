package com.company.batchmonitor.batch.jobs;

import com.company.batchmonitor.domain.BatchJobLog;
import com.company.batchmonitor.domain.ExternalResult;
import com.company.batchmonitor.domain.ReconciliationResult;
import com.company.batchmonitor.domain.SettlementRequest;
import com.company.batchmonitor.repository.BatchJobLogRepository;
import com.company.batchmonitor.repository.ExternalResultRepository;
import com.company.batchmonitor.repository.ReconciliationResultRepository;
import com.company.batchmonitor.repository.SettlementRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SettlementVerificationBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SettlementRequestRepository settlementRequestRepository;
    private final ExternalResultRepository externalResultRepository;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final BatchJobLogRepository batchJobLogRepository;

    @Bean
    public Job settlementVerificationJob() {
        return new JobBuilder("settlementVerificationJob", jobRepository)
                .listener(settlementVerificationJobListener())
                .start(verifySettlementsStep())
                .build();
    }

    @Bean
    public Step verifySettlementsStep() {
        return new StepBuilder("verifySettlementsStep", jobRepository)
                .tasklet(settlementReconciliationTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Tasklet settlementReconciliationTasklet() {
        return (contribution, chunkContext) -> {
            String mockFailure = String.valueOf(chunkContext.getStepContext().getJobParameters().get("mockFailure"));
            if (Boolean.parseBoolean(mockFailure)) {
                throw new IllegalStateException("Mock reconciliation failure requested.");
            }

            List<SettlementRequest> requests = settlementRequestRepository.findAll();
            List<ExternalResult> externalResults = externalResultRepository.findAll();
            log.info("Loaded {} settlement requests and {} external results", requests.size(), externalResults.size());

            Map<Long, ExternalResult> externalMap = externalResults.stream()
                    .filter(result -> result.getSettlementRequestId() != null)
                    .sorted(Comparator.comparing(ExternalResult::getReceivedAt).reversed())
                    .collect(Collectors.toMap(ExternalResult::getSettlementRequestId, result -> result, (first, ignored) -> first));

            int successCount = 0;
            int failCount = 0;
            List<ReconciliationResult> results = new ArrayList<>();

            for (SettlementRequest request : requests) {
                ExternalResult external = externalMap.get(request.getId());
                String resultType;
                String reason;

                if (external == null) {
                    if ("APPROVED".equals(request.getStatus())) {
                        resultType = "MISSING_EXTERNAL";
                        reason = "Approved settlement has no external result.";
                        failCount++;
                    } else {
                        request.updateReconciliation("PENDING", "UNVERIFIED", null);
                        settlementRequestRepository.save(request);
                        continue;
                    }
                } else if (!"APPROVED".equals(request.getStatus())) {
                    resultType = "INVALID_STATUS";
                    reason = "External result exists for a settlement that is not approved: " + request.getStatus();
                    failCount++;
                } else if (request.getAmount().equals(external.getExternalAmount())) {
                    resultType = "MATCHED";
                    reason = "Internal and external amounts match.";
                    successCount++;
                } else {
                    resultType = "MISMATCHED";
                    reason = "Internal amount " + request.getAmount() + " differs from external amount " + external.getExternalAmount() + ".";
                    failCount++;
                }

                request.updateReconciliation("SUCCESS", resultType, external != null ? external.getExternalAmount() : null);
                settlementRequestRepository.save(request);
                results.add(toResult(request.getId(), resultType, request.getAmount(), external != null ? external.getExternalAmount() : null, reason));
            }

            for (ExternalResult external : externalResults) {
                boolean hasInternal = external.getSettlementRequestId() != null
                        && settlementRequestRepository.existsById(external.getSettlementRequestId());
                if (!hasInternal) {
                    failCount++;
                    results.add(toResult(
                            external.getSettlementRequestId(),
                            "UNKNOWN_EXTERNAL",
                            null,
                            external.getExternalAmount(),
                            "External result has no matching internal settlement: " + external.getExternalTransactionId()
                    ));
                }
            }

            reconciliationResultRepository.deleteAllInBatch();
            reconciliationResultRepository.saveAll(results);

            JobExecution jobExecution = chunkContext.getStepContext().getStepExecution().getJobExecution();
            jobExecution.getExecutionContext().put("successCount", successCount);
            jobExecution.getExecutionContext().put("failCount", failCount);
            log.info("Reconciliation completed: successCount={}, failCount={}", successCount, failCount);

            return RepeatStatus.FINISHED;
        };
    }

    private ReconciliationResult toResult(Long requestId, String resultType, Long internalAmount, Long externalAmount, String reason) {
        return ReconciliationResult.builder()
                .settlementRequestId(requestId)
                .resultType(resultType)
                .internalAmount(internalAmount)
                .externalAmount(externalAmount)
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Bean
    public JobExecutionListener settlementVerificationJobListener() {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                Long logId = jobExecution.getJobParameters().getLong("logId");
                if (logId == null) {
                    BatchJobLog jobLog = BatchJobLog.builder()
                            .jobName(jobExecution.getJobInstance().getJobName())
                            .startedAt(LocalDateTime.now())
                            .status("RUNNING")
                            .successCount(0)
                            .failCount(0)
                            .retryCount(0)
                            .isRetried(false)
                            .build();
                    batchJobLogRepository.save(jobLog);
                    jobExecution.getExecutionContext().put("logId", jobLog.getId());
                } else {
                    jobExecution.getExecutionContext().put("logId", logId);
                }
            }

            @Override
            public void afterJob(JobExecution jobExecution) {
                Long logId = jobExecution.getExecutionContext().getLong("logId");
                BatchJobLog jobLog = batchJobLogRepository.findById(logId).orElse(null);
                if (jobLog == null) {
                    return;
                }

                int successCount = jobExecution.getExecutionContext().containsKey("successCount")
                        ? jobExecution.getExecutionContext().getInt("successCount")
                        : 0;
                int failCount = jobExecution.getExecutionContext().containsKey("failCount")
                        ? jobExecution.getExecutionContext().getInt("failCount")
                        : 0;

                if (jobExecution.getStatus().isUnsuccessful() || failCount > 0) {
                    String errorMessage = !jobExecution.getAllFailureExceptions().isEmpty()
                            ? jobExecution.getAllFailureExceptions().get(0).getMessage()
                            : "Reconciliation completed with " + failCount + " failed item(s).";
                    jobLog.complete("FAILED", successCount, failCount, errorMessage);
                } else {
                    jobLog.complete("SUCCESS", successCount, 0, "Reconciliation completed successfully.");
                }
                batchJobLogRepository.save(jobLog);
            }
        };
    }
}

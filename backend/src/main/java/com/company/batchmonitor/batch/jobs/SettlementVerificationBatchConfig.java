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
            log.info(">>>>> [Reconciliation Step] 정산 데이터 정합성 검증 배치 가동");

            // 1. 데이터 전체 수집
            List<SettlementRequest> requests = settlementRequestRepository.findAll();
            List<ExternalResult> externalResults = externalResultRepository.findAll();

            log.info(">>>>> 내부 정산 요청 건수: {}건, 외부 연계 실적 건수: {}건", requests.size(), externalResults.size());

            // 2. 외부 실적을 정산 요청 ID 기준으로 Map 캐싱 (ID 중복 시 첫 번째 값 채택)
            Map<Long, ExternalResult> externalMap = externalResults.stream()
                    .filter(e -> e.getSettlementRequestId() != null)
                    .collect(Collectors.toMap(ExternalResult::getSettlementRequestId, e -> e, (e1, e2) -> e1));

            int successCount = 0;
            int failCount = 0;
            List<ReconciliationResult> reconResults = new ArrayList<>();

            // 3. 정합성 대조 검증 수행

            // A. 내부 정산 요청 기준 검증 (규칙 1, 2, 3, 5)
            for (SettlementRequest req : requests) {
                ExternalResult ext = externalMap.get(req.getId());
                String reconStatus;
                String reason;

                if (ext != null) {
                    if ("APPROVED".equals(req.getStatus())) {
                        if (req.getAmount().equals(ext.getExternalAmount())) {
                            reconStatus = "MATCHED";
                            reason = "내부 승인 정산 요청과 외부 실적 데이터의 금액이 일치합니다.";
                            successCount++;
                        } else {
                            reconStatus = "MISMATCHED";
                            reason = "내부 승인 금액(" + req.getAmount() + ")과 외부 금액(" + ext.getExternalAmount() + ")이 불일치합니다.";
                            failCount++;
                        }
                    } else {
                        reconStatus = "INVALID_STATUS";
                        reason = "내부 요청 상태가 APPROVED가 아님(" + req.getStatus() + ")에도 외부 실적이 존재합니다.";
                        failCount++;
                    }
                } else {
                    if ("APPROVED".equals(req.getStatus())) {
                        reconStatus = "MISSING_EXTERNAL";
                        reason = "내부 정산 요청이 APPROVED 상태이나 외부 실적 데이터가 존재하지 않습니다.";
                        failCount++;
                    } else {
                        reconStatus = "UNVERIFIED";
                        reason = "미승인 건 및 외부 데이터 없음 (검증 보류)";
                    }
                }

                req.updateReconciliation("SUCCESS", reconStatus, ext != null ? ext.getExternalAmount() : null);
                settlementRequestRepository.save(req);

                // 검증 보류 건(UNVERIFIED)을 제외하고 결과 내역에 기록
                if (!"UNVERIFIED".equals(reconStatus)) {
                    ReconciliationResult reconRes = ReconciliationResult.builder()
                            .settlementRequestId(req.getId())
                            .resultType(reconStatus)
                            .internalAmount(req.getAmount())
                            .externalAmount(ext != null ? ext.getExternalAmount() : null)
                            .reason(reason)
                            .createdAt(LocalDateTime.now())
                            .build();
                    reconResults.add(reconRes);
                }
            }

            // B. 외부 실적 기준 검증 (규칙 4 - UNKNOWN_EXTERNAL)
            for (ExternalResult ext : externalResults) {
                boolean hasInternal = false;
                if (ext.getSettlementRequestId() != null) {
                    hasInternal = settlementRequestRepository.existsById(ext.getSettlementRequestId());
                }

                if (!hasInternal) {
                    String reconStatus = "UNKNOWN_EXTERNAL";
                    String reason = "내부 정산 요청 기록이 없으나 외부 연계 실적만 존재합니다. (외부 거래 ID: " + ext.getExternalTransactionId() + ")";
                    failCount++;

                    ReconciliationResult reconRes = ReconciliationResult.builder()
                            .settlementRequestId(ext.getSettlementRequestId())
                            .resultType(reconStatus)
                            .internalAmount(null)
                            .externalAmount(ext.getExternalAmount())
                            .reason(reason)
                            .createdAt(LocalDateTime.now())
                            .build();
                    reconResults.add(reconRes);
                }
            }

            // 결과 일괄 저장
            reconciliationResultRepository.saveAll(reconResults);

            log.info(">>>>> 검증 배치 처리 완료 (성공: {}건, 정합성 위배: {}건)", successCount, failCount);

            // JobExecutionContext에 카운트 전달하여 리스너에서 DB 로그 생성에 활용 가능하도록 처리
            chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext().put("successCount", successCount);
            chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext().put("failCount", failCount);

            // 정합성 오류가 발생한 경우 배치를 실패 판정하여 재처리 대상이 되도록 예외 처리
            if (failCount > 0) {
                throw new RuntimeException("정합성 검증 불일치/누락 건이 " + failCount + "건 발생하여 배치를 실패 처리합니다. (RECONCILIATION_ERROR)");
            }

            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public JobExecutionListener settlementVerificationJobListener() {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                log.info("====== settlementVerificationJob 가동 전 리스너 작동 ======");
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
                log.info("====== settlementVerificationJob 가동 완료 리스너 작동 ======");
                Long logId = jobExecution.getExecutionContext().getLong("logId");
                
                BatchJobLog jobLog = batchJobLogRepository.findById(logId).orElse(null);
                if (jobLog != null) {
                    String status = jobExecution.getStatus().toString();
                    String errorMsg = jobExecution.getExitStatus().getExitDescription();
                    
                    int successCount = jobExecution.getExecutionContext().containsKey("successCount") ? 
                            jobExecution.getExecutionContext().getInt("successCount") : 0;
                    int failCount = jobExecution.getExecutionContext().containsKey("failCount") ? 
                            jobExecution.getExecutionContext().getInt("failCount") : 0;

                    if (jobExecution.getStatus().isUnsuccessful()) {
                        status = "FAILED";
                        if (!jobExecution.getAllFailureExceptions().isEmpty()) {
                            errorMsg = jobExecution.getAllFailureExceptions().get(0).getMessage();
                        }
                    } else {
                        status = "SUCCESS";
                        errorMsg = "정산 검증 배치가 정상 완료되었습니다.";
                    }
                    
                    jobLog.complete(status, successCount, failCount, errorMsg);
                    batchJobLogRepository.save(jobLog);
                }
            }
        };
    }
}

package com.company.batchmonitor.batch.jobs;

import com.company.batchmonitor.domain.BatchJobHistory;
import com.company.batchmonitor.domain.SettlementRequest;
import com.company.batchmonitor.repository.BatchJobHistoryRepository;
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
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SettlementVerificationBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SettlementRequestRepository settlementRequestRepository;
    private final BatchJobHistoryRepository batchJobHistoryRepository;

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

            // 1. 검증 대상(APPROVED 상태의 정산 요청) 조회
            List<SettlementRequest> targets = settlementRequestRepository.findByStatus("APPROVED");
            if (targets.isEmpty()) {
                log.info(">>>>> 검증 대상 정산 요청 건이 존재하지 않습니다.");
                return RepeatStatus.FINISHED;
            }

            log.info(">>>>> 총 {}건의 정산 요청에 대한 검증을 시작합니다.", targets.size());

            // 모의 에러 발생 옵션 체크
            Object isMockFailure = chunkContext.getStepContext().getJobParameters().get("mockFailure");
            boolean triggerFailure = isMockFailure != null && "true".equals(isMockFailure.toString());

            int mismatchCount = 0;
            int errorCount = 0;

            for (SettlementRequest request : targets) {
                // 2. 연계 기관 실거래 정산 내역 API 호출 및 수신 (테스트 파라미터가 켜진 경우 일부 불일치 데이터 반환)
                long mockExternalAmount;
                
                if (triggerFailure && request.getId() % 2 == 1) {
                    // 금액 대조 불일치 테스트 케이스 시뮬레이션
                    mockExternalAmount = request.getAmount() - 50000; 
                } else {
                    mockExternalAmount = request.getAmount(); 
                }

                // 3. 정합성 대조 검증 수행
                if (mockExternalAmount == request.getAmount()) {
                    request.updateReconciliation("SUCCESS", "MATCHED", mockExternalAmount);
                    log.info(">> [MATCHED] ID: {} - 내부 정산액과 외부 정산액 일치 ({}원)", request.getId(), request.getAmount());
                } else {
                    request.updateReconciliation("SUCCESS", "MISMATCHED", mockExternalAmount);
                    log.error(">> [MISMATCHED] ID: {} - 내부 정산액({}) != 외부 정산액({})", 
                            request.getId(), request.getAmount(), mockExternalAmount);
                    mismatchCount++;
                }

                settlementRequestRepository.save(request);
            }

            // 4. 검증 결과에 따른 배치 결과 판정 (실패 재처리 매커니즘 연계)
            if (mismatchCount > 0) {
                throw new RuntimeException("정산 정합성 검증 실패 건이 " + mismatchCount + "건 존재하여 배치를 중단합니다. (RECONCILIATION_ERROR)");
            }

            log.info(">>>>> 모든 정산 데이터 정합성 검증 통과 (SUCCESS)");
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public JobExecutionListener settlementVerificationJobListener() {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                log.info("====== settlementVerificationJob 가동 전 리스너 작동 ======");
                Long historyId = (Long) jobExecution.getJobParameters().getLong("historyId");
                
                if (historyId == null) {
                    BatchJobHistory history = BatchJobHistory.builder()
                            .jobName(jobExecution.getJob().getName())
                            .startTime(LocalDateTime.now())
                            .status("RUNNING")
                            .retryCount(0)
                            .isRetried(false)
                            .build();
                    batchJobHistoryRepository.save(history);
                    jobExecution.getExecutionContext().put("historyId", history.getId());
                } else {
                    jobExecution.getExecutionContext().put("historyId", historyId);
                }
            }

            @Override
            public void afterJob(JobExecution jobExecution) {
                log.info("====== settlementVerificationJob 가동 완료 리스너 작동 ======");
                Long historyId = jobExecution.getExecutionContext().getLong("historyId");
                
                BatchJobHistory history = batchJobHistoryRepository.findById(historyId).orElse(null);
                if (history != null) {
                    String status = jobExecution.getStatus().toString();
                    String exitMsg = jobExecution.getExitStatus().getExitDescription();
                    
                    if (jobExecution.getStatus().isUnsuccessful()) {
                        status = "FAILED";
                        if (!jobExecution.getAllFailureExceptions().isEmpty()) {
                            exitMsg = jobExecution.getAllFailureExceptions().get(0).getMessage();
                        }
                    } else {
                        status = "SUCCESS";
                        exitMsg = "정산 검증 배치가 정상 완료되었습니다.";
                    }
                    
                    history.complete(status, exitMsg);
                    batchJobHistoryRepository.save(history);
                }
            }
        };
    }
}

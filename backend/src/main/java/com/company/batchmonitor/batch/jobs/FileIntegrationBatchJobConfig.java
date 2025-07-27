package com.company.batchmonitor.batch.jobs;

import com.company.batchmonitor.domain.BatchJobLog;
import com.company.batchmonitor.repository.BatchJobLogRepository;
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

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FileIntegrationBatchJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BatchJobLogRepository batchJobLogRepository;

    @Bean
    public Job fileIntegrationJob() {
        return new JobBuilder("fileIntegrationJob", jobRepository)
                .listener(jobExecutionListener())
                .start(fileDownloadStep())
                .next(fileProcessStep())
                .build();
    }

    @Bean
    public Step fileDownloadStep() {
        return new StepBuilder("fileDownloadStep", jobRepository)
                .tasklet(fileDownloadTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Step fileProcessStep() {
        return new StepBuilder("fileProcessStep", jobRepository)
                .tasklet(fileProcessTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Tasklet fileDownloadTasklet() {
        return (contribution, chunkContext) -> {
            log.info(">>>>> [Step 1] 외부 연계 파일 다운로드 및 S3 백업 작업 시작");
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Tasklet fileProcessTasklet() {
        return (contribution, chunkContext) -> {
            log.info(">>>>> [Step 2] 연계 데이터 파싱 및 파이프라인 처리 시작");
            
            Object isMockFailure = chunkContext.getStepContext().getJobParameters().get("mockFailure");
            if (isMockFailure != null && "true".equals(isMockFailure.toString())) {
                log.error(">>>>> [Step 2] 금융 연계 전문 데이터 정합성 검증 실패 (HASH_MISMATCH)");
                throw new RuntimeException("금융 결제 전문의 송신 해시 값이 일치하지 않습니다.");
            }

            log.info(">>>>> [Step 2] 연계 데이터 120건 정상 처리 및 DB 적재 완료.");
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public JobExecutionListener jobExecutionListener() {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                log.info("====== fileIntegrationJob 배치 가동 전 리스너 작동 ======");
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
                log.info("====== fileIntegrationJob 배치 가동 완료 리스너 작동 ======");
                Long logId = jobExecution.getExecutionContext().getLong("logId");
                
                BatchJobLog jobLog = batchJobLogRepository.findById(logId).orElse(null);
                if (jobLog != null) {
                    String status = jobExecution.getStatus().toString();
                    String errorMsg = jobExecution.getExitStatus().getExitDescription();
                    
                    if (jobExecution.getStatus().isUnsuccessful()) {
                        status = "FAILED";
                        if (!jobExecution.getAllFailureExceptions().isEmpty()) {
                            errorMsg = jobExecution.getAllFailureExceptions().get(0).getMessage();
                        }
                    } else {
                        status = "SUCCESS";
                        errorMsg = "배치가 성공적으로 완료되었습니다.";
                    }
                    
                    jobLog.complete(status, 120, 0, errorMsg);
                    batchJobLogRepository.save(jobLog);
                }
            }
        };
    }
}

package com.company.batchmonitor.batch.jobs;

import com.company.batchmonitor.domain.BatchJobHistory;
import com.company.batchmonitor.repository.BatchJobHistoryRepository;
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
    private final BatchJobHistoryRepository batchJobHistoryRepository;

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
            // SFTP 서버에서 수신 대상 파일을 로컬 Temp 디렉토리로 가져오는 연계 영역
            
            log.info(">>>>> [Step 1] 연계 파일 수신 완료.");
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Tasklet fileProcessTasklet() {
        return (contribution, chunkContext) -> {
            log.info(">>>>> [Step 2] 연계 데이터 파싱 및 파이프라인 처리 시작");
            
            // 테스트 시뮬레이션용 실패 파라미터 확인
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
                Long historyId = (Long) jobExecution.getJobParameters().getLong("historyId");
                
                // REST API 수동 기동 시 이미 생성된 이력을 가져오고, Scheduler 자동 기동일 경우 이 시점에 신규 이력을 생성
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
                log.info("====== fileIntegrationJob 배치 가동 완료 리스너 작동 ======");
                Long historyId = jobExecution.getExecutionContext().getLong("historyId");
                
                BatchJobHistory history = batchJobHistoryRepository.findById(historyId).orElse(null);
                if (history != null) {
                    String status = jobExecution.getStatus().toString();
                    String exitMsg = jobExecution.getExitStatus().getExitDescription();
                    
                    if (jobExecution.getStatus().isUnsuccessful()) {
                        status = "FAILED";
                        // 예외 스택트레이스 파싱하여 저장
                        if (jobExecution.getAllFailureExceptions().size() > 0) {
                            exitMsg = jobExecution.getAllFailureExceptions().get(0).getMessage();
                        }
                    } else {
                        status = "SUCCESS";
                        exitMsg = "배치가 성공적으로 완료되었습니다.";
                    }
                    
                    history.complete(status, exitMsg);
                    batchJobHistoryRepository.save(history);
                }
            }
        };
    }
}

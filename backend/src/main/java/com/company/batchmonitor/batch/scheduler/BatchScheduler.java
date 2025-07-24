package com.company.batchmonitor.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job fileIntegrationJob;

    // 매 5분마다 자동으로 외부 연계 배치 실행 (Cron)
    @Scheduled(cron = "0 */5 * * * *")
    public void runFileIntegrationJob() {
        log.info(">>>>> [Scheduler Trigger] 외부기관 연계 배치 스케줄 기동 시작: {}", LocalDateTime.now());
        
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("datetime", LocalDateTime.now().toString())
                    .addString("triggeredBy", "Scheduler")
                    .toJobParameters();

            jobLauncher.run(fileIntegrationJob, jobParameters);
        } catch (Exception e) {
            log.error("Failed to run scheduled file integration job", e);
        }
    }
}

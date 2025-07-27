package com.company.batchmonitor.global.config;

import com.company.batchmonitor.domain.*;
import com.company.batchmonitor.repository.BatchJobLogRepository;
import com.company.batchmonitor.repository.ExternalResultRepository;
import com.company.batchmonitor.repository.SettlementRequestRepository;
import com.company.batchmonitor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BatchJobLogRepository batchJobLogRepository;
    private final SettlementRequestRepository settlementRequestRepository;
    private final ExternalResultRepository externalResultRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("====== Starting Default Data Initialization ======");

        // 1. 기본 관리자 및 사용자 생성
        User admin = userRepository.findByUsername("admin").orElseGet(() -> {
            User u = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("password"))
                    .name("김철수 주무관")
                    .role(Role.ROLE_ADMIN)
                    .build();
            userRepository.save(u);
            log.info("Created default administrator: admin / password");
            return u;
        });

        User operator = userRepository.findByUsername("operator").orElseGet(() -> {
            User u = User.builder()
                    .username("operator")
                    .password(passwordEncoder.encode("password"))
                    .name("이영희 대리")
                    .role(Role.ROLE_OPERATOR)
                    .build();
            userRepository.save(u);
            log.info("Created default operator: operator / password");
            return u;
        });

        User user = userRepository.findByUsername("user").orElseGet(() -> {
            User u = User.builder()
                    .username("user")
                    .password(passwordEncoder.encode("password"))
                    .name("박민수 사원")
                    .role(Role.ROLE_USER)
                    .build();
            userRepository.save(u);
            log.info("Created default user: user / password");
            return u;
        });

        // 2. 기본 배치 실행 이력 생성
        if (batchJobLogRepository.count() == 0) {
            BatchJobLog jobLog = BatchJobLog.builder()
                    .jobName("settlementVerificationJob")
                    .startedAt(LocalDateTime.now().minusHours(4))
                    .finishedAt(LocalDateTime.now().minusHours(4).plusSeconds(3))
                    .status("SUCCESS")
                    .successCount(1)
                    .failCount(0)
                    .errorMessage("정산 검증 배치가 정상 완료되었습니다.")
                    .retryCount(0)
                    .isRetried(false)
                    .build();
            batchJobLogRepository.save(jobLog);
            log.info("Created default batch log records.");
        }

        // 3. 테스트용 정산 요청 및 외부 Mock 실적 데이터 생성
        if (settlementRequestRepository.count() == 0) {
            
            // 시나리오 1. 정상 일치 (MATCHED)
            SettlementRequest req1 = SettlementRequest.builder()
                    .title("[정상] 2026년 6월 농협 연계 수수료 청구")
                    .amount(1500000L)
                    .requester(user)
                    .assignedOperator(operator)
                    .status("APPROVED")
                    .externalSyncStatus("PENDING")
                    .reconciliationStatus("UNVERIFIED")
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .updatedAt(LocalDateTime.now().minusHours(2))
                    .build();
            req1 = settlementRequestRepository.save(req1);

            ExternalResult ext1 = ExternalResult.builder()
                    .externalTransactionId("TX_MOCK_001_MATCHED")
                    .settlementRequestId(req1.getId())
                    .externalAmount(1500000L)
                    .externalStatus("APPROVED")
                    .receivedAt(LocalDateTime.now().minusHours(1))
                    .build();
            externalResultRepository.save(ext1);

            // 시나리오 2. 금액 불일치 (MISMATCHED)
            SettlementRequest req2 = SettlementRequest.builder()
                    .title("[금액불일치] 2026년 6월 국민카드 정산 결제")
                    .amount(2500000L)
                    .requester(user)
                    .assignedOperator(operator)
                    .status("APPROVED")
                    .externalSyncStatus("PENDING")
                    .reconciliationStatus("UNVERIFIED")
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .updatedAt(LocalDateTime.now().minusHours(3))
                    .build();
            req2 = settlementRequestRepository.save(req2);

            ExternalResult ext2 = ExternalResult.builder()
                    .externalTransactionId("TX_MOCK_002_MISMATCHED")
                    .settlementRequestId(req2.getId())
                    .externalAmount(2400000L) // 10만원 차이 발생
                    .externalStatus("APPROVED")
                    .receivedAt(LocalDateTime.now().minusHours(1))
                    .build();
            externalResultRepository.save(ext2);

            // 시나리오 3. 외부 데이터 누락 (MISSING_EXTERNAL)
            SettlementRequest req3 = SettlementRequest.builder()
                    .title("[외부누락] 2026년 5월 소급분 지방세 수납")
                    .amount(3500000L)
                    .requester(user)
                    .assignedOperator(admin)
                    .status("APPROVED")
                    .externalSyncStatus("PENDING")
                    .reconciliationStatus("UNVERIFIED")
                    .createdAt(LocalDateTime.now().minusDays(3))
                    .updatedAt(LocalDateTime.now().minusHours(4))
                    .build();
            settlementRequestRepository.save(req3);
            // 외부 실적 없음

            // 시나리오 4. 내부 요청 없이 외부 데이터만 존재 (UNKNOWN_EXTERNAL)
            ExternalResult ext4 = ExternalResult.builder()
                    .externalTransactionId("TX_MOCK_004_UNKNOWN")
                    .settlementRequestId(null) // 매핑 없음
                    .externalAmount(800000L)
                    .externalStatus("APPROVED")
                    .receivedAt(LocalDateTime.now().minusHours(1))
                    .build();
            externalResultRepository.save(ext4);

            // 시나리오 5. 승인되지 않은 요청에 대한 외부 실적 존재 (INVALID_STATUS)
            SettlementRequest req5 = SettlementRequest.builder()
                    .title("[미승인실적] 2026년 6월 카카오페이 가맹점 정산")
                    .amount(4500000L)
                    .requester(user)
                    .assignedOperator(operator)
                    .status("REQUESTED") // 승인 안됨
                    .externalSyncStatus("PENDING")
                    .reconciliationStatus("UNVERIFIED")
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .updatedAt(LocalDateTime.now().minusHours(5))
                    .build();
            req5 = settlementRequestRepository.save(req5);

            ExternalResult ext5 = ExternalResult.builder()
                    .externalTransactionId("TX_MOCK_005_INVALID")
                    .settlementRequestId(req5.getId())
                    .externalAmount(4500000L)
                    .externalStatus("APPROVED")
                    .receivedAt(LocalDateTime.now().minusHours(1))
                    .build();
            externalResultRepository.save(ext5);

            log.info("Created default mock settlement requests and external results for 5 verification scenarios.");
        }

        log.info("====== Default Data Initialization Completed ======");
    }
}

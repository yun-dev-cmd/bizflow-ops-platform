package com.company.batchmonitor.global.config;

import com.company.batchmonitor.domain.BatchJobHistory;
import com.company.batchmonitor.domain.Role;
import com.company.batchmonitor.domain.SettlementRequest;
import com.company.batchmonitor.domain.User;
import com.company.batchmonitor.repository.BatchJobHistoryRepository;
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
    private final BatchJobHistoryRepository batchJobHistoryRepository;
    private final SettlementRequestRepository settlementRequestRepository;
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
        if (batchJobHistoryRepository.count() == 0) {
            BatchJobHistory history1 = BatchJobHistory.builder()
                    .jobName("settlementVerificationJob")
                    .startTime(LocalDateTime.now().minusHours(4))
                    .endTime(LocalDateTime.now().minusHours(4).plusSeconds(6))
                    .status("SUCCESS")
                    .exitMessage("정산 검증 배치가 정상 완료되었습니다.")
                    .retryCount(0)
                    .isRetried(false)
                    .build();
            batchJobHistoryRepository.save(history1);
            log.info("Created default batch history records.");
        }

        // 3. 테스트용 정산 요청 기본 데이터 생성
        if (settlementRequestRepository.count() == 0) {
            // 건 1: 요청 상태
            SettlementRequest req1 = SettlementRequest.builder()
                    .title("2026년 6월 상반기 농협 수수료 요청")
                    .amount(1250000L)
                    .requester(user)
                    .status("REQUESTED")
                    .externalSyncStatus("PENDING")
                    .reconciliationStatus("UNVERIFIED")
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .updatedAt(LocalDateTime.now().minusDays(1))
                    .build();
            settlementRequestRepository.save(req1);

            // 건 2: 담당자 지정 상태
            SettlementRequest req2 = SettlementRequest.builder()
                    .title("2026년 6월 2차 국민카드 정산 결제 청구")
                    .amount(5800000L)
                    .requester(user)
                    .assignee(operator)
                    .status("ASSIGNED")
                    .externalSyncStatus("PENDING")
                    .reconciliationStatus("UNVERIFIED")
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .updatedAt(LocalDateTime.now().minusHours(5))
                    .build();
            settlementRequestRepository.save(req2);

            // 건 3: 승인 완료 상태 (배치 정합성 검사 타겟)
            SettlementRequest req3 = SettlementRequest.builder()
                    .title("2026년 5월 소급분 지방세 수납 정산")
                    .amount(3400000L)
                    .requester(user)
                    .assignee(admin)
                    .status("APPROVED")
                    .externalSyncStatus("PENDING")
                    .reconciliationStatus("UNVERIFIED")
                    .createdAt(LocalDateTime.now().minusDays(3))
                    .updatedAt(LocalDateTime.now().minusHours(1))
                    .build();
            settlementRequestRepository.save(req3);

            log.info("Created default mock settlement requests.");
        }

        log.info("====== Default Data Initialization Completed ======");
    }
}

package com.company.batchmonitor.global.config;

import com.company.batchmonitor.domain.BatchJobLog;
import com.company.batchmonitor.domain.ExternalResult;
import com.company.batchmonitor.domain.Role;
import com.company.batchmonitor.domain.SettlementRequest;
import com.company.batchmonitor.domain.User;
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
    public void run(String... args) {
        log.info("Starting default data initialization");

        User admin = userRepository.findByUsername("admin").orElseGet(() -> {
            User user = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("password"))
                    .name("Default Admin")
                    .role(Role.ROLE_ADMIN)
                    .build();
            userRepository.save(user);
            log.info("Created default admin account");
            return user;
        });

        User operator = userRepository.findByUsername("operator").orElseGet(() -> {
            User user = User.builder()
                    .username("operator")
                    .password(passwordEncoder.encode("password"))
                    .name("Default Operator")
                    .role(Role.ROLE_OPERATOR)
                    .build();
            userRepository.save(user);
            log.info("Created default operator account");
            return user;
        });

        User requester = userRepository.findByUsername("user").orElseGet(() -> {
            User user = User.builder()
                    .username("user")
                    .password(passwordEncoder.encode("password"))
                    .name("Default User")
                    .role(Role.ROLE_USER)
                    .build();
            userRepository.save(user);
            log.info("Created default user account");
            return user;
        });

        if (batchJobLogRepository.count() == 0) {
            BatchJobLog jobLog = BatchJobLog.builder()
                    .jobName("settlementVerificationJob")
                    .startedAt(LocalDateTime.now().minusHours(4))
                    .finishedAt(LocalDateTime.now().minusHours(4).plusSeconds(3))
                    .status("SUCCESS")
                    .successCount(1)
                    .failCount(0)
                    .errorMessage("Initial batch log seed")
                    .retryCount(0)
                    .isRetried(false)
                    .build();
            batchJobLogRepository.save(jobLog);
        }

        if (settlementRequestRepository.count() == 0) {
            SettlementRequest matched = SettlementRequest.builder()
                    .title("[MATCHED] June platform settlement fee")
                    .amount(1_500_000L)
                    .requester(requester)
                    .assignedOperator(operator)
                    .status("APPROVED")
                    .externalSyncStatus("PENDING")
                    .reconciliationStatus("UNVERIFIED")
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .updatedAt(LocalDateTime.now().minusHours(2))
                    .build();
            matched = settlementRequestRepository.save(matched);

            externalResultRepository.save(ExternalResult.builder()
                    .externalTransactionId("TX_MOCK_001_MATCHED")
                    .settlementRequestId(matched.getId())
                    .externalAmount(1_500_000L)
                    .externalStatus("APPROVED")
                    .receivedAt(LocalDateTime.now().minusHours(1))
                    .build());

            SettlementRequest mismatched = SettlementRequest.builder()
                    .title("[MISMATCHED] June card settlement")
                    .amount(2_500_000L)
                    .requester(requester)
                    .assignedOperator(operator)
                    .status("APPROVED")
                    .externalSyncStatus("PENDING")
                    .reconciliationStatus("UNVERIFIED")
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .updatedAt(LocalDateTime.now().minusHours(3))
                    .build();
            mismatched = settlementRequestRepository.save(mismatched);

            externalResultRepository.save(ExternalResult.builder()
                    .externalTransactionId("TX_MOCK_002_MISMATCHED")
                    .settlementRequestId(mismatched.getId())
                    .externalAmount(2_400_000L)
                    .externalStatus("APPROVED")
                    .receivedAt(LocalDateTime.now().minusHours(1))
                    .build());

            settlementRequestRepository.save(SettlementRequest.builder()
                    .title("[MISSING_EXTERNAL] May local tax payment")
                    .amount(3_500_000L)
                    .requester(requester)
                    .assignedOperator(admin)
                    .status("APPROVED")
                    .externalSyncStatus("PENDING")
                    .reconciliationStatus("UNVERIFIED")
                    .createdAt(LocalDateTime.now().minusDays(3))
                    .updatedAt(LocalDateTime.now().minusHours(4))
                    .build());

            externalResultRepository.save(ExternalResult.builder()
                    .externalTransactionId("TX_MOCK_004_UNKNOWN")
                    .settlementRequestId(null)
                    .externalAmount(800_000L)
                    .externalStatus("APPROVED")
                    .receivedAt(LocalDateTime.now().minusHours(1))
                    .build());

            SettlementRequest invalid = SettlementRequest.builder()
                    .title("[INVALID_STATUS] June franchise settlement")
                    .amount(4_500_000L)
                    .requester(requester)
                    .assignedOperator(operator)
                    .status("REQUESTED")
                    .externalSyncStatus("PENDING")
                    .reconciliationStatus("UNVERIFIED")
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .updatedAt(LocalDateTime.now().minusHours(5))
                    .build();
            invalid = settlementRequestRepository.save(invalid);

            externalResultRepository.save(ExternalResult.builder()
                    .externalTransactionId("TX_MOCK_005_INVALID")
                    .settlementRequestId(invalid.getId())
                    .externalAmount(4_500_000L)
                    .externalStatus("APPROVED")
                    .receivedAt(LocalDateTime.now().minusHours(1))
                    .build());
        }

        log.info("Default data initialization completed");
    }
}

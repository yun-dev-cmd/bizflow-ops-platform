package com.company.batchmonitor;

import com.company.batchmonitor.domain.ExternalResult;
import com.company.batchmonitor.domain.Role;
import com.company.batchmonitor.domain.SettlementRequest;
import com.company.batchmonitor.domain.User;
import com.company.batchmonitor.repository.ExternalResultRepository;
import com.company.batchmonitor.repository.SettlementRequestRepository;
import com.company.batchmonitor.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SettlementVerificationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SettlementRequestRepository settlementRequestRepository;

    @Autowired
    private ExternalResultRepository externalResultRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("test_user_verify")
                .password("password")
                .name("홍길동")
                .role(Role.ROLE_USER)
                .build();
        userRepository.save(testUser);
    }

    @Test
    @DisplayName("정합성 검증 시나리오 테스트: APPROVED 정산 요청과 외부 실적이 일치하는 경우")
    void testMatchedReconciliation() {
        // given
        SettlementRequest req = SettlementRequest.builder()
                .title("테스트 정산 요청 건")
                .amount(500000L)
                .requester(testUser)
                .status("APPROVED")
                .externalSyncStatus("PENDING")
                .reconciliationStatus("UNVERIFIED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        req = settlementRequestRepository.save(req);

        ExternalResult ext = ExternalResult.builder()
                .externalTransactionId("TX_TEST_001")
                .settlementRequestId(req.getId())
                .externalAmount(500000L) // 동일 금액
                .externalStatus("APPROVED")
                .receivedAt(LocalDateTime.now())
                .build();
        externalResultRepository.save(ext);

        // when (정합성 대조 로직 시뮬레이션)
        SettlementRequest loadedReq = settlementRequestRepository.findById(req.getId()).orElseThrow();
        ExternalResult loadedExt = externalResultRepository.findBySettlementRequestId(loadedReq.getId()).orElseThrow();

        String statusResult;
        if ("APPROVED".equals(loadedReq.getStatus()) && loadedReq.getAmount().equals(loadedExt.getExternalAmount())) {
            statusResult = "MATCHED";
        } else {
            statusResult = "MISMATCHED";
        }

        // then
        assertThat(statusResult).isEqualTo("MATCHED");
    }
}

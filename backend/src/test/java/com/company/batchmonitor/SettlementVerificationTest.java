package com.company.batchmonitor;

import com.company.batchmonitor.domain.Role;
import com.company.batchmonitor.domain.User;
import com.company.batchmonitor.repository.AttachmentRepository;
import com.company.batchmonitor.repository.BatchJobLogRepository;
import com.company.batchmonitor.repository.ExternalResultRepository;
import com.company.batchmonitor.repository.ReconciliationResultRepository;
import com.company.batchmonitor.repository.SettlementRequestRepository;
import com.company.batchmonitor.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettlementVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SettlementRequestRepository settlementRequestRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private ExternalResultRepository externalResultRepository;

    @Autowired
    private ReconciliationResultRepository reconciliationResultRepository;

    @Autowired
    private BatchJobLogRepository batchJobLogRepository;

    @MockBean
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        reconciliationResultRepository.deleteAll();
        externalResultRepository.deleteAll();
        batchJobLogRepository.deleteAll();
        settlementRequestRepository.deleteAll();
        attachmentRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(user("admin", Role.ROLE_ADMIN));
        userRepository.save(user("operator", Role.ROLE_OPERATOR));
        userRepository.save(user("user", Role.ROLE_USER));

        doThrow(new RuntimeException("S3 disabled in tests"))
                .when(s3Client)
                .putObject(ArgumentMatchers.any(PutObjectRequest.class), ArgumentMatchers.any(RequestBody.class));
    }

    @Test
    void readmeDashboardFlowWorksThroughBackendApis() throws Exception {
        String adminToken = login("admin");
        String operatorToken = login("operator");
        String userToken = login("user");

        long matchedSettlementId = createSettlement(userToken, "matched request", 120000L);
        uploadAttachment(userToken, matchedSettlementId);
        assignAndApprove(operatorToken, matchedSettlementId);
        createExternalMock(operatorToken, "TX_MATCHED", matchedSettlementId, 120000L);

        runReconciliation(operatorToken);

        JsonNode results = getJson(operatorToken, "/api/reconciliation-results");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("resultType").asText()).isEqualTo("MATCHED");

        JsonNode logs = getJson(operatorToken, "/api/batches/logs");
        assertThat(logs.get(0).get("status").asText()).isEqualTo("SUCCESS");
        assertThat(logs.get(0).get("successCount").asInt()).isEqualTo(1);
        assertThat(logs.get(0).get("failCount").asInt()).isZero();

        JsonNode summary = getJson(userToken, "/api/dashboard/summary");
        assertThat(summary.get("totalRequests").asLong()).isEqualTo(1);
        assertThat(summary.get("approvedRequests").asLong()).isEqualTo(1);
        assertThat(summary.get("matchedCount").asLong()).isEqualTo(1);
        assertThat(summary.get("lastBatchStatus").asText()).isEqualTo("SUCCESS");

        long retrySettlementId = createSettlement(userToken, "retry request", 500000L);
        assignAndApprove(operatorToken, retrySettlementId);
        createExternalMock(operatorToken, "TX_RETRY_BAD", retrySettlementId, 400000L);

        runReconciliation(operatorToken);

        JsonNode failedLogs = getJson(operatorToken, "/api/batches/logs");
        long failedLogId = failedLogs.get(0).get("id").asLong();
        assertThat(failedLogs.get(0).get("status").asText()).isEqualTo("FAILED");
        assertThat(failedLogs.get(0).get("failCount").asInt()).isGreaterThan(0);

        createExternalMock(operatorToken, "TX_RETRY_FIXED", retrySettlementId, 500000L);
        retryReconciliation(adminToken, failedLogId);

        JsonNode retriedLogs = getJson(adminToken, "/api/batches/logs");
        assertThat(retriedLogs.get(0).get("status").asText()).isEqualTo("SUCCESS");
        assertThat(retriedLogs.get(1).get("isRetried").asBoolean()).isTrue();

        JsonNode retriedSummary = getJson(adminToken, "/api/dashboard/summary");
        assertThat(retriedSummary.get("lastBatchStatus").asText()).isEqualTo("SUCCESS");
        assertThat(retriedSummary.get("matchedCount").asLong()).isEqualTo(2);
    }

    private User user(String username, Role role) {
        return User.builder()
                .username(username)
                .password(passwordEncoder.encode("password"))
                .name(username)
                .role(role)
                .build();
    }

    private String login(String username) throws Exception {
        JsonNode response = postJson(null, "/api/auth/login", Map.of(
                "username", username,
                "password", "password"
        ));
        assertThat(response.get("role").asText()).isEqualTo(expectedRole(username));
        return response.get("token").asText();
    }

    private String expectedRole(String username) {
        return switch (username) {
            case "admin" -> "ROLE_ADMIN";
            case "operator" -> "ROLE_OPERATOR";
            case "user" -> "ROLE_USER";
            default -> throw new IllegalArgumentException("Unexpected test user: " + username);
        };
    }

    private long createSettlement(String token, String title, long amount) throws Exception {
        JsonNode response = postJson(token, "/api/settlements", Map.of(
                "title", title,
                "amount", amount
        ));
        assertThat(response.get("status").asText()).isEqualTo("REQUESTED");
        return response.get("id").asLong();
    }

    private void uploadAttachment(String token, long settlementId) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "evidence.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "evidence".getBytes()
        );

        mockMvc.perform(multipart("/api/settlements/{id}/attachments", settlementId)
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void assignAndApprove(String token, long settlementId) throws Exception {
        mockMvc.perform(patch("/api/settlements/{id}/assign", settlementId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("assigneeUsername", "operator"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/settlements/{id}/approve", settlementId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void createExternalMock(String token, String transactionId, long settlementId, long amount) throws Exception {
        postJson(token, "/api/external-results/mock", Map.of(
                "externalTransactionId", transactionId,
                "settlementRequestId", settlementId,
                "externalAmount", amount,
                "externalStatus", "APPROVED"
        ));
    }

    private void runReconciliation(String token) throws Exception {
        mockMvc.perform(post("/api/batches/reconciliation/run")
                        .param("mockFailure", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void retryReconciliation(String token, long logId) throws Exception {
        postJson(token, "/api/batches/reconciliation/retry", Map.of("logId", logId));
    }

    private JsonNode getJson(String token, String path) throws Exception {
        String body = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode postJson(String token, String path, Object body) throws Exception {
        var request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        String response = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }
}

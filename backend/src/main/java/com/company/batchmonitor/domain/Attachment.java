package com.company.batchmonitor.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_request_id")
    private Long settlementRequestId;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String storedPath; // S3 Key 또는 로컬 저장된 유니크 파일명/경로

    @Column(nullable = false)
    private String storageType; // LOCAL, S3_MOCK

    @Column(nullable = false)
    private String uploadedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

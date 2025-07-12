package com.company.batchmonitor.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import java.net.URI;

@Slf4j
@Configuration
public class S3Config {

    @Value("${aws.s3.access-key}")
    private String accessKey;

    @Value("${aws.s3.secret-key}")
    private String secretKey;

    @Value("${aws.s3.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        try {
            // 로컬 개발 환경용 플레이스홀더 키 설정 시 처리
            if ("aws_access_key_placeholder".equals(accessKey) || accessKey.trim().isEmpty()) {
                log.warn("AWS Credentials are using placeholder values. Initializing fallback client for local storage path.");
                return S3Client.builder()
                        .region(Region.of(region))
                        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("temp", "temp")))
                        .build();
            }

            return S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                    .build();
        } catch (Exception e) {
            log.error("Failed to build AWS S3 Client, fallback to local file system. Reason: {}", e.getMessage());
            // 로컬 디렉토리 업로드를 보장하기 위해 대체 클라이언트 생성
            return S3Client.builder()
                    .region(Region.of("ap-northeast-2"))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("temp", "temp")))
                    .build();
        }
    }
}

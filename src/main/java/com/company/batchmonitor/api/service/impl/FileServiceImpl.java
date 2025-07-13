package com.company.batchmonitor.api.service.impl;

import com.company.batchmonitor.api.service.FileService;
import com.company.batchmonitor.domain.Attachment;
import com.company.batchmonitor.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final AttachmentRepository attachmentRepository;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    // 로컬 백업용 디렉토리 (S3 에러 발생 시 Fallback 저장소)
    private final String localUploadDir = "E:/Aws/uploads";

    @Override
    @Transactional
    public Attachment uploadFile(MultipartFile file, String username) throws IOException {
        String originalFileName = file.getOriginalFilename();
        String storedFileName = UUID.randomUUID().toString() + "_" + originalFileName;
        long fileSize = file.getSize();
        String fileUrl;

        try {
            // 1. AWS S3 업로드 시도
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storedFileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), fileSize));
            fileUrl = String.format("https://%s.s3.ap-northeast-2.amazonaws.com/%s", bucketName, storedFileName);
            log.info("Successfully uploaded file to AWS S3: key={}", storedFileName);

        } catch (Exception e) {
            log.warn("AWS S3 upload failed. Fallback to Local Storage. Reason: {}", e.getMessage());
            
            // 2. Fallback: 로컬 디스크 저장
            File directory = new File(localUploadDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            Path targetPath = Paths.get(localUploadDir).resolve(storedFileName);
            Files.copy(file.getInputStream(), targetPath);
            
            fileUrl = targetPath.toAbsolutePath().toString();
            log.info("Successfully saved file to Local: path={}", fileUrl);
        }

        // 3. DB 메타데이터 저장
        Attachment attachment = Attachment.builder()
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .fileSize(fileSize)
                .fileUrl(fileUrl)
                .uploadedBy(username)
                .uploadedAt(LocalDateTime.now())
                .build();

        return attachmentRepository.save(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadFile(Long fileId) throws IOException {
        Attachment attachment = getFileMetadata(fileId);
        String storedFileName = attachment.getStoredFileName();

        try {
            // 1. AWS S3에서 파일 조회 시도
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storedFileName)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
            log.info("Successfully downloaded file from AWS S3: key={}", storedFileName);
            return objectBytes.asByteArray();

        } catch (Exception e) {
            log.warn("AWS S3 download failed. Trying local storage fallback. Reason: {}", e.getMessage());

            // 2. Fallback: 로컬 저장소에서 파일 읽기
            Path localPath = Paths.get(localUploadDir).resolve(storedFileName);
            if (Files.exists(localPath)) {
                log.info("Successfully retrieved file from Local: path={}", localPath);
                return Files.readAllBytes(localPath);
            }
            
            throw new IOException("파일을 찾을 수 없습니다. (S3 및 로컬 저장소 모두 조회 실패)", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Attachment getFileMetadata(Long fileId) {
        return attachmentRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 파일 이력 ID입니다."));
    }
}

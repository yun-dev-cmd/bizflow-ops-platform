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

    @Value("${aws.s3.local-only:false}")
    private boolean localOnly;

    // 로컬 백업용 디렉토리 (S3 에러 발생 시 Fallback 저장소) - 상대경로로 변경하여 이식성 증대
    private final String localUploadDir = "./uploads";

    @Override
    @Transactional
    public Attachment uploadFile(MultipartFile file, String username, Long settlementRequestId) throws IOException {
        String originalFileName = file.getOriginalFilename();
        String storedFileName = UUID.randomUUID().toString() + "_" + originalFileName;
        long fileSize = file.getSize();
        String storedPath;
        String storageType = "S3_MOCK";

        if (localOnly) {
            storedPath = saveToLocal(file, storedFileName);
            return saveAttachmentMetadata(settlementRequestId, originalFileName, storedPath, "LOCAL", username);
        }

        try {
            // 1. AWS S3 업로드 시도 (Mock S3 환경이거나 환경변수 비정상 시 exception 발생)
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storedFileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), fileSize));
            storedPath = String.format("https://%s.s3.ap-northeast-2.amazonaws.com/%s", bucketName, storedFileName);
            log.info("Successfully uploaded file to AWS S3: key={}", storedFileName);

        } catch (Exception e) {
            log.warn("AWS S3 upload failed. Fallback to Local Storage. Reason: {}", e.getMessage());
            
            // 2. Fallback: 로컬 디스크 저장
            storedPath = saveToLocal(file, storedFileName);
            storageType = "LOCAL";
            log.info("Successfully saved file to Local: path={}", storedPath);
        }

        // 3. DB 메타데이터 저장
        Attachment attachment = Attachment.builder()
                .settlementRequestId(settlementRequestId)
                .originalFileName(originalFileName)
                .storedPath(storedPath)
                .storageType(storageType)
                .uploadedBy(username)
                .createdAt(LocalDateTime.now())
                .build();

        return attachmentRepository.save(attachment);
    }

    private String saveToLocal(MultipartFile file, String storedFileName) throws IOException {
        File directory = new File(localUploadDir);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        Path targetPath = Paths.get(localUploadDir).resolve(storedFileName);
        Files.copy(file.getInputStream(), targetPath);
        return targetPath.toAbsolutePath().toString();
    }

    private Attachment saveAttachmentMetadata(Long settlementRequestId, String originalFileName, String storedPath, String storageType, String username) {
        Attachment attachment = Attachment.builder()
                .settlementRequestId(settlementRequestId)
                .originalFileName(originalFileName)
                .storedPath(storedPath)
                .storageType(storageType)
                .uploadedBy(username)
                .createdAt(LocalDateTime.now())
                .build();

        return attachmentRepository.save(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadFile(Long fileId) throws IOException {
        Attachment attachment = getFileMetadata(fileId);
        String storedPath = attachment.getStoredPath();
        String storageType = attachment.getStorageType();

        if ("LOCAL".equals(storageType)) {
            // 로컬 저장소에서 파일 읽기
            Path path = Paths.get(storedPath);
            if (Files.exists(path)) {
                log.info("Successfully retrieved file from Local: path={}", path);
                return Files.readAllBytes(path);
            }
            // 만약 상대경로 결합이 필요한 경우 처리
            String fileName = path.getFileName().toString();
            Path fallbackPath = Paths.get(localUploadDir).resolve(fileName);
            if (Files.exists(fallbackPath)) {
                log.info("Successfully retrieved file from Local Fallback: path={}", fallbackPath);
                return Files.readAllBytes(fallbackPath);
            }
        } else {
            try {
                // AWS S3에서 파일 조회 시도
                String storedFileName = storedPath.substring(storedPath.lastIndexOf("/") + 1);
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(storedFileName)
                        .build();

                ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
                log.info("Successfully downloaded file from AWS S3: key={}", storedFileName);
                return objectBytes.asByteArray();

            } catch (Exception e) {
                log.warn("AWS S3 download failed. Trying local storage fallback. Reason: {}", e.getMessage());
            }
        }

        // 최후의 수단: 파일 이름으로 로컬 디렉토리 검색
        String fileNameOnly = storedPath.substring(storedPath.lastIndexOf(File.separator) + 1);
        if (fileNameOnly.contains("/")) {
            fileNameOnly = fileNameOnly.substring(fileNameOnly.lastIndexOf("/") + 1);
        }
        Path localPath = Paths.get(localUploadDir).resolve(fileNameOnly);
        if (Files.exists(localPath)) {
            log.info("Fallback: Successfully retrieved file from Local relative path: path={}", localPath);
            return Files.readAllBytes(localPath);
        }

        throw new IOException("파일을 찾을 수 없습니다. (S3 및 로컬 저장소 모두 조회 실패)");
    }

    @Override
    @Transactional(readOnly = true)
    public Attachment getFileMetadata(Long fileId) {
        return attachmentRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 파일 이력 ID입니다."));
    }
}

package com.company.batchmonitor.api.controller;

import com.company.batchmonitor.api.service.FileService;
import com.company.batchmonitor.domain.Attachment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "File Management", description = "AWS S3 / 로컬 연계 파일 업로드 및 다운로드 API")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Attachment> uploadFile(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        Attachment attachment = fileService.uploadFile(file, userDetails.getUsername(), null);
        return ResponseEntity.ok(attachment);
    }

    @Operation(summary = "연계 파일 다운로드", description = "파일 ID를 기반으로 파일을 다운로드합니다. (OPERATOR, ADMIN 권한 필요)")
    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long id) throws IOException {
        Attachment metadata = fileService.getFileMetadata(id);
        byte[] fileData = fileService.downloadFile(id);

        String encodedFileName = URLEncoder.encode(metadata.getOriginalFileName(), StandardCharsets.UTF_8.toString())
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                .body(fileData);
    }
}

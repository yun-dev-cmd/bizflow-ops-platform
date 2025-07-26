package com.company.batchmonitor.api.service;

import com.company.batchmonitor.domain.Attachment;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface FileService {
    Attachment uploadFile(MultipartFile file, String username, Long settlementRequestId) throws IOException;
    byte[] downloadFile(Long fileId) throws IOException;
    Attachment getFileMetadata(Long fileId);
}

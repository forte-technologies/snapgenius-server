package dev.forte.mygenius.ai.uploads;

import java.time.LocalDateTime;
import java.util.UUID;

public class UploadDTO {
    private UUID uploadId;
    private String fileName;
    private String fileType;
    private LocalDateTime createdAt;

    public UUID getUploadId() {
        return uploadId;
    }

    public void setUploadId(UUID uploadId) {
        this.uploadId = uploadId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public static UploadDTO fromEntity(Upload upload) {
        UploadDTO dto = new UploadDTO();
        dto.setUploadId(upload.getUploadId());
        dto.setFileName(upload.getFileName());
        dto.setFileType(upload.getFileType());
        dto.setCreatedAt(upload.getCreatedAt());
        return dto;
    }
}
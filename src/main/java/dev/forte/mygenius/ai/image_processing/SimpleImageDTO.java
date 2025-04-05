package dev.forte.mygenius.ai.image_processing;

import java.time.LocalDateTime;
import java.util.UUID;

public class SimpleImageDTO {

    private String fileName;
    private UUID userId;
    private LocalDateTime createdAt;

    public SimpleImageDTO(String fileName, UUID userId, LocalDateTime createdAt) {
        this.fileName = fileName;
        this.userId = userId;
        this.createdAt = createdAt;
    }


    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}


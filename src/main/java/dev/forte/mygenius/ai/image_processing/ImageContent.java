package dev.forte.mygenius.ai.image_processing;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "image_contents")
public class ImageContent {
    @Id
    @GeneratedValue
    private UUID contentId;

    @Column(name = "image_id", nullable = false)
    private UUID imageId;

    @Column(name = "extracted_text")
    private String extractedText;

    @Column(name = "generated_description")
    private String generatedDescription;

    @Column(name = "processing_status")
    private String processingStatus;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "content_data", columnDefinition = "bytea")
    private byte[] contentData;

    // Add missing getters and setters
    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public String getGeneratedDescription() {
        return generatedDescription;
    }

    public void setGeneratedDescription(String generatedDescription) {
        this.generatedDescription = generatedDescription;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    // Keep existing getters and setters
    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public UUID getImageId() {
        return imageId;
    }

    public void setImageId(UUID imageId) {
        this.imageId = imageId;
    }

    public byte[] getContentData() {
        return contentData;
    }

    public void setContentData(byte[] contentData) {
        this.contentData = contentData;
    }
}
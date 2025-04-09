package dev.forte.mygenius.ai.document_processing;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_contents")
public class DocumentContents {


    @Id
    @GeneratedValue
    @Column(name = "content_id")
    private UUID contentId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "extracted_text")
    private String extractedText;

    @Column(name = "processing_status")
    private String processingStatus;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "content_data", columnDefinition = "bytea")
    private byte[] contentData;

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
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

    public byte[] getContentData() {
        return contentData;
    }

    public void setContentData(byte[] contentData) {
        this.contentData = contentData;
    }
}

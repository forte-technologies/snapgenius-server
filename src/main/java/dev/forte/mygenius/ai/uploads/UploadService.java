package dev.forte.mygenius.ai.uploads;

import dev.forte.mygenius.ai.document_processing.DocumentContents;
import dev.forte.mygenius.ai.document_processing.DocumentContentsRepository;
import dev.forte.mygenius.ai.document_processing.DocumentRepository;
import dev.forte.mygenius.ai.image_processing.ImageContent;
import dev.forte.mygenius.ai.image_processing.ImageContentRepository;
import dev.forte.mygenius.ai.image_processing.ImageRepository;
import dev.forte.mygenius.errors.ResourceNotFoundException;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class UploadService {

    private final UploadRepository uploadRepository;
    private final DocumentRepository documentRepository;
    private final DocumentContentsRepository documentContentsRepository;
    private final ImageRepository imageRepository;
    private final ImageContentRepository imageContentRepository;
    private final JdbcTemplate jdbcTemplate;

    public UploadService(UploadRepository uploadRepository,
                         DocumentRepository documentRepository,
                         DocumentContentsRepository documentContentsRepository,
                         ImageRepository imageRepository,
                         ImageContentRepository imageContentRepository,
                         JdbcTemplate jdbcTemplate) {
        this.uploadRepository = uploadRepository;
        this.documentRepository = documentRepository;
        this.documentContentsRepository = documentContentsRepository;
        this.imageRepository = imageRepository;
        this.imageContentRepository = imageContentRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void deleteUpload(UUID uploadId, UUID userId) {
        // Find the upload and verify it belongs to the user
        Upload upload = uploadRepository.findByUploadIdAndUserId(uploadId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Upload not found or doesn't belong to user"));

        UUID contentId = upload.getContentId();

        if ("IMAGE".equals(upload.getFileType())) {
            // Get the content_id from image_contents for vector deletion
            Optional<ImageContent> imageContent = imageContentRepository.findByImageId(contentId);

            if (imageContent.isPresent()) {
                // Delete vector embeddings directly via SQL
                deleteVectorEmbeddings(imageContent.get().getContentId());

                // Delete the image content
                imageContentRepository.deleteByImageId(contentId);
            }

            // Delete the image
            imageRepository.deleteById(contentId);
        }
        else if ("DOCUMENT".equals(upload.getFileType())) {
            // Get the content_id from document_contents for vector deletion
            Optional<DocumentContents> documentContent = documentContentsRepository.findByDocumentId(contentId);

            if (documentContent.isPresent()) {
                // Delete vector embeddings directly via SQL
                deleteVectorEmbeddings(documentContent.get().getContentId());

                // For document_contents, we have to delete it explicitly since your interface
                // doesn't appear to have a deleteByDocumentId method
                documentContentsRepository.deleteById(documentContent.get().getContentId());
            }

            // Now delete the document
            documentRepository.deleteById(contentId);
        }

        // Finally delete the upload record
        uploadRepository.deleteById(uploadId);
    }

    private void deleteVectorEmbeddings(UUID contentId) {
        // Updated SQL to search within the metadata JSONB field
        String sql = "DELETE FROM vector_store WHERE metadata->>'content_id' = ?";
        jdbcTemplate.update(sql, contentId.toString());
    }

    public Page<UploadDTO> getUserUploadDTOs(UUID userId, Pageable pageable) {
        Page<Upload> uploads = uploadRepository.findByUserId(userId, pageable);
        return uploads.map(UploadDTO::fromEntity);
    }
}
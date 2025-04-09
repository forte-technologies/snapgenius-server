package dev.forte.mygenius.ai.document_processing;


import dev.forte.mygenius.security.CustomUserPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user/documents")
public class DocumentController {

    private final DocumentProcessingService documentProcessingService;


    public DocumentController(DocumentProcessingService documentProcessingService) {
        this.documentProcessingService = documentProcessingService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String,Object>> uploadDocument(
            @AuthenticationPrincipal CustomUserPrincipal userPrincipal,
            @RequestParam("file") MultipartFile file) {

        try {
            // Get user ID from authentication principal
            UUID userId = userPrincipal.getUserId();

            // Start async processing
            documentProcessingService.processUserDocuments(userId, file);

            // Return accepted status with basic information
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Document upload initiated");
            response.put("fileName", file.getOriginalFilename());
            response.put("fileSize", file.getSize());
            response.put("status", "processing");

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to upload document: " + e.getMessage()));
        }

    }
}

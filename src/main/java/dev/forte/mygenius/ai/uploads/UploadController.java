package dev.forte.mygenius.ai.uploads;

import dev.forte.mygenius.security.CustomUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getUserUploads(
            @AuthenticationPrincipal CustomUserPrincipal userPrincipal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        UUID userId = userPrincipal.getUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<UploadDTO> uploadsPage = uploadService.getUserUploadDTOs(userId, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("uploads", uploadsPage.getContent());
        response.put("currentPage", uploadsPage.getNumber());
        response.put("totalItems", uploadsPage.getTotalElements());
        response.put("totalPages", uploadsPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<?> deleteUpload(
            @PathVariable UUID uploadId,
            @AuthenticationPrincipal CustomUserPrincipal userPrincipal) {

        UUID userId = userPrincipal.getUserId();
        uploadService.deleteUpload(uploadId, userId);
        return ResponseEntity.noContent().build();
    }
}
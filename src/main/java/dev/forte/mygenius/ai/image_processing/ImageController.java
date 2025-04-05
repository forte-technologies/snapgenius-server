package dev.forte.mygenius.ai.image_processing;

import dev.forte.mygenius.ai.ImageProcessingService;
import dev.forte.mygenius.security.CustomUserPrincipal;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user/images")
public class ImageController {
    private final ImageProcessingService imageProcessingService;

    public ImageController(ImageProcessingService imageProcessingService) {
        this.imageProcessingService = imageProcessingService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadImage(
            @AuthenticationPrincipal CustomUserPrincipal userPrincipal,
            @RequestParam("file") MultipartFile file) {

        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "File is empty"));
            }

            // Process the image
            imageProcessingService.processUserImage(userPrincipal.getUserId(), file);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Image processed successfully");
            response.put("fileName", file.getOriginalFilename());
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error processing image: " + e.getMessage()));
        }
    }

    @GetMapping("/imageFileNames")
    public ResponseEntity<Map<String, Object>> getImageFileNames(@AuthenticationPrincipal CustomUserPrincipal userPrincipal,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "10") int size) {
        UUID userId = userPrincipal.getUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<SimpleImageDTO> imageNamePage = imageProcessingService.getImageFileNames(userId, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("imageNames", imageNamePage.getContent());
        response.put("currentPage", imageNamePage.getNumber());
        response.put("totalItems", imageNamePage.getTotalElements());
        response.put("totalPages", imageNamePage.getTotalPages());

        return ResponseEntity.ok(response);

    }
}
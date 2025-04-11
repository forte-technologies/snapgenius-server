package dev.forte.mygenius.ai.uploads;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadRepository extends JpaRepository<Upload, UUID> {
    Page<Upload> findByUserId(UUID userId, Pageable pageable);
    Optional<Upload> findByUploadIdAndUserId(UUID uploadId, UUID userId);
}
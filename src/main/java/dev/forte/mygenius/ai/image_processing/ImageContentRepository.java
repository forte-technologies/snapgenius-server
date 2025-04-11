package dev.forte.mygenius.ai.image_processing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImageContentRepository extends JpaRepository<ImageContent, UUID> {
    Optional<ImageContent> findByImageId(UUID imageId);

    void deleteByImageId(UUID contentId);
}
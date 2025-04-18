package dev.forte.mygenius.ai.image_processing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImageRepository extends JpaRepository<Image, UUID> {
    List<Image> findByUserId(UUID userId);

    @Query("SELECT new dev.forte.mygenius.ai.image_processing.SimpleImageDTO(i.fileName, i.userId, i.createdAt) FROM Image i WHERE i.userId = :userId")
    Page<SimpleImageDTO> findSimpleIMagesByUserId(@Param("userId")UUID userId, Pageable pageable);

}
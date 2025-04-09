package dev.forte.mygenius.ai.document_processing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentContentsRepository extends JpaRepository<DocumentContents, UUID> {


}

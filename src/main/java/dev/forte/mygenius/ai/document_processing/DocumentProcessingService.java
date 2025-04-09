package dev.forte.mygenius.ai.document_processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final DocumentContentsRepository documentContentsRepository;
    private final OpenAiChatModel openAiChatModel;
    private final PgVectorStore pgVectorStore;
    private final ObjectMapper objectMapper;


    public DocumentProcessingService(DocumentRepository documentRepository, DocumentContentsRepository documentContentsRepository,
                                     @Qualifier("openAiApiJson") OpenAiChatModel openAiChatModel, PgVectorStore pgVectorStore, ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.documentContentsRepository = documentContentsRepository;
        this.openAiChatModel = openAiChatModel;
        this.pgVectorStore = pgVectorStore;
        this.objectMapper = objectMapper;

    }

    public void processUserDocuments(UUID userId, MultipartFile file) throws IOException {
        UserDocument document = new UserDocument();
        document.setUserId(userId);
        document.setFileName(file.getOriginalFilename());
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setCreatedAt(LocalDateTime.now());
        documentRepository.save(document);
        log.info("Document saved to database: {}", document.getFileName());

        DocumentContents content = new DocumentContents();
        content.setDocumentId(document.getDocumentId());
        content.setContentData(file.getBytes());
        content.setProcessingStatus("pending");
        documentContentsRepository.save(content);
        log.info("Document content saved to database");

        try {
            // Extract text using Tika
            ByteArrayResource resource = new ByteArrayResource(content.getContentData());
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();
            String extractedText = "";

            if (!documents.isEmpty()) {
                Document doc = documents.getFirst();
                if (doc.isText()) {
                    extractedText = doc.getText();
                }
            }

            // Update document content with extracted text
            content.setExtractedText(extractedText);
            content.setProcessingStatus("processed");
            content.setProcessedAt(LocalDateTime.now());
            documentContentsRepository.save(content);
            log.info("Text extracted successfully, length: {} characters", extractedText.length());

            // Split into paragraphs
            List<String> paragraphs = Arrays.stream(extractedText.split("\\n\\s*\\n"))
                    .filter(para -> !para.trim().isEmpty())
                    .collect(Collectors.toList());

            // If there are very few paragraphs, try sentence splitting
            if (paragraphs.size() < 3 && extractedText.length() > 1000) {
                paragraphs = Arrays.stream(extractedText.split("(?<=[.!?])\\s+"))
                        .filter(sent -> !sent.trim().isEmpty())
                        .collect(Collectors.toList());
            }

            // Combine paragraphs into appropriately sized chunks
            List<String> properChunks = new ArrayList<>();
            StringBuilder currentChunk = new StringBuilder();
            int minWords = 100; // Aim for at least 100 words per chunk
            int maxWords = 500; // But no more than 500 words

            for (String paragraph : paragraphs) {
                int currentWords = currentChunk.toString().split("\\s+").length;
                int paragraphWords = paragraph.split("\\s+").length;

                if (currentWords + paragraphWords <= maxWords) {
                    // Add to current chunk if within max size
                    if (currentChunk.length() > 0) {
                        currentChunk.append("\n\n");
                    }
                    currentChunk.append(paragraph);
                } else if (currentWords >= minWords) {
                    // Current chunk is big enough, save it and start new chunk
                    properChunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder(paragraph);
                } else {
                    // Current chunk is too small, but adding paragraph would exceed max
                    // Split the paragraph to fit what we can
                    String[] words = paragraph.split("\\s+");
                    int wordsToAdd = maxWords - currentWords;

                    // Add as many words as we can to current chunk
                    if (!currentChunk.isEmpty()) {
                        currentChunk.append("\n\n");
                    }
                    currentChunk.append(String.join(" ", Arrays.copyOfRange(words, 0, Math.min(wordsToAdd, words.length))));

                    // Save current chunk
                    properChunks.add(currentChunk.toString());

                    // Start new chunk with remaining words if any
                    if (wordsToAdd < words.length) {
                        currentChunk = new StringBuilder(String.join(" ",
                                Arrays.copyOfRange(words, wordsToAdd, words.length)));
                    } else {
                        currentChunk = new StringBuilder();
                    }
                }
            }

            // Add the final chunk if it's not empty and meets minimum size
            if (!currentChunk.isEmpty()) {
                int finalChunkWords = currentChunk.toString().split("\\s+").length;
                if (finalChunkWords >= minWords) {
                    properChunks.add(currentChunk.toString());
                } else if (!properChunks.isEmpty()) {
                    // If final chunk is too small, append it to the last chunk
                    String lastChunk = properChunks.remove(properChunks.size() - 1);
                    properChunks.add(lastChunk + "\n\n" + currentChunk);
                } else {
                    // If this is the only chunk, keep it despite being small
                    properChunks.add(currentChunk.toString());
                }
            }

            // Special case: if the entire document is very small, use it as a single chunk
            if (properChunks.isEmpty() && !paragraphs.isEmpty()) {
                properChunks.add(extractedText);
            }

            log.info("Document split into {} chunks", properChunks.size());

            // Process each chunk and add to vector store
            List<Document> vectorDocuments = new ArrayList<>();
            int index = 0;

            for (String chunkText : properChunks) {
                // Only process if the chunk is substantial
                if (chunkText.split("\\s+").length >= 10) {
                    try {
                        // Get topic for this specific chunk
                        ChatResponse topicResponse = openAiChatModel.call(
                                new Prompt(
                                        new SystemMessage("Provide a concise topic label (3-7 words) that summarizes the main subject of this text."),
                                        new UserMessage(chunkText)
                                )
                        );

                        String topic = topicResponse.getResult().getOutput().getText().trim();
                        log.info("Generated topic for chunk {}: {} {}", index, topic, chunkText);

                        // Create metadata for the chunk
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("user_id", userId.toString());
                        metadata.put("document_id", document.getDocumentId().toString());
                        metadata.put("content_id", content.getContentId().toString());
                        metadata.put("chunk_index", index);
                        metadata.put("topic", topic);

                        // Create document for vector store
                        Document vectorDocument = new Document(chunkText, metadata);
                        vectorDocuments.add(vectorDocument);

                        index++;

                    } catch (Exception e) {
                        log.error("Error getting topic for chunk {}: {}", index, e.getMessage());

                        // Use a fallback topic if LLM call fails
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("user_id", userId.toString());
                        metadata.put("document_id", document.getDocumentId().toString());
                        metadata.put("content_id", content.getContentId().toString());
                        metadata.put("chunk_index", index);
                        metadata.put("topic", "Document Section " + (index + 1));

                        Document vectorDocument = new Document(chunkText, metadata);
                        vectorDocuments.add(vectorDocument);

                        index++;
                    }
                }
            }

            // Add all documents to vector store at once
            if (!vectorDocuments.isEmpty()) {
                pgVectorStore.add(vectorDocuments);
                log.info("Added {} chunks to vector store", vectorDocuments.size());
            }
        } catch (Exception e) {
            log.error("Error processing document: {}", e.getMessage(), e);
            content.setProcessingStatus("failed");
            content.setProcessedAt(LocalDateTime.now());
            documentContentsRepository.save(content);
        }
    }
}
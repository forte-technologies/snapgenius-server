package dev.forte.mygenius.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.forte.mygenius.ai.image_processing.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.Media;
import org.springframework.ai.openai.OpenAiChatModel;

import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static org.springframework.ai.model.Media.Format.IMAGE_JPEG;

@Service
public class ImageProcessingService {
    private final ImageRepository imageRepository;
    private final ImageContentRepository contentRepository;
    private final PgVectorStore vectorStore;
    private final OpenAiChatModel visionModel;
    private final ObjectMapper objectMapper;

    public ImageProcessingService(ImageRepository imageRepository, ImageContentRepository contentRepository, PgVectorStore vectorStore,
                                  @Qualifier("openAiApi") OpenAiChatModel visionModel, ObjectMapper objectMapper) {
        this.imageRepository = imageRepository;
        this.contentRepository = contentRepository;
        this.vectorStore = vectorStore;
        this.visionModel = visionModel;
        this.objectMapper = objectMapper;
    }

    public void processUserImage(UUID userId, MultipartFile file) throws IOException {
        // 1. Store image metadata
        Image image = new Image();
        image.setUserId(userId);
        image.setFileName(file.getOriginalFilename());
        image.setFileSize(file.getSize());
        image.setMimeType(file.getContentType());
        image.setCreatedAt(LocalDateTime.now());
        imageRepository.save(image);

        // 2. Store binary image data
        ImageContent content = new ImageContent();
        content.setImageId(image.getImageId());
        content.setContentData(file.getBytes());
        content.setProcessingStatus("pending"); // Set status
        content.setExtractedText(null); // Will be filled later
        content.setGeneratedDescription(null); // Will be filled after OpenAI processing
        ImageContent savedContent = contentRepository.save(content);

        // 3. Generate base64 encoding of image for OpenAI
        String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
        String dataUrl = "data:image/jpeg;base64," + base64Image;

        Media imageMedia = Media.builder()
                .mimeType(Media.Format.IMAGE_JPEG)
                .data(dataUrl)  // Pass the data URL string directly here
                .build();

        String imageDescription = ChatClient.builder(visionModel)
                .build()
                .prompt()
                .system("You are an assistant that does two things: If an image is uploaded without text, you will generate a detailed description of the image. " +
                        "If the image does have text, you must parse the text from the image, word for word. Do not summarize it, just parse the text in its entirety.")
                .user(u -> u.text("This is the upload:")
                        .media(imageMedia))
                .call()
                .content();

        System.out.println(imageDescription);

        savedContent.setGeneratedDescription(imageDescription);
        savedContent.setProcessingStatus("completed");
        contentRepository.save(savedContent);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user_id", userId.toString());
        metadata.put("content_id", savedContent.getContentId().toString());
        metadata.put("content_type", "description");

        Document document = new Document(imageDescription, metadata);


        System.out.println("Embedding the image description");
        vectorStore.add(Collections.singletonList(document));
    }

    public Page<SimpleImageDTO> getImageFileNames(UUID userId, Pageable pageable) {
      return imageRepository.findSimpleIMagesByUserId(userId, pageable);

    }
}
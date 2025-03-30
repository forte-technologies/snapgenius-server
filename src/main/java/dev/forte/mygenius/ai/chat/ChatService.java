package dev.forte.mygenius.ai.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatService {

    private final OpenAiChatModel openAiChatModel;
    private final VectorStore vectorStore;

    // Configuration parameters
    private static final int MAX_MEMORY_HOURS = 24;
    private static final int MAX_MESSAGES_PER_USER = 15; // Adjust based on token budget
    private final ChatMemory chatMemory;

    public ChatService(OpenAiChatModel openAiChatModel, VectorStore vectorStore, ChatMemory chatMemory) {
        this.openAiChatModel = openAiChatModel;
        this.vectorStore = vectorStore;
        this.chatMemory = chatMemory;
    }

    public String ragAssistantO(UUID userId, String message){

        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold(.01)
                        .topK(5)
                        .filterExpression(new FilterExpressionBuilder()
                                .eq("user_id", userId.toString())
                                .build())
                        .vectorStore(vectorStore)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();

        return ChatClient.builder(openAiChatModel)
                .build()
                .prompt()
                .system("You are an AI assistant that helps users with information about images and documents they have uploaded. " +
                        "You have access to descriptions of images in the user's collection. " +
                        "When answering questions, ONLY use the image descriptions provided in your context. " +
                        "If you don't have relevant image descriptions in your context, say 'I don't have information about that in your uploads.' " +
                        "DO NOT make up information about images the user has not uploaded. " +
                        "If the user asks about a dog, cat, or other subject, check if you have a matching image description before responding.")
                .advisors(retrievalAugmentationAdvisor)
                .user(message)
                .call()
                .content();
    }


}

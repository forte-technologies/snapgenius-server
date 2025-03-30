package dev.forte.mygenius.ai.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.*;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Service
public class ChatServiceV2 {
    private final ChatClient chatClient;

    private final Map<UUID, LocalDateTime> lastAccessTimes = new ConcurrentHashMap<>();

    public ChatServiceV2(ChatClient.Builder builder, VectorStore vectorStore, ChatMemory chatMemory) {


        this.chatClient = builder
                .defaultSystem("You are an AI assistant that helps users with information about images and documents they have uploaded. " +
                        "You have access to descriptions of images in the user's collection. " +
                        "When answering questions, ONLY use the image descriptions provided in your context and what the user has told you. " +
                        "Retain the information that the user has told you, especially personal information. " +
                        "If you don't have relevant image descriptions in your context, say 'I don't have information about that in your uploads.' " +
                        "DO NOT make up information about images the user has not uploaded. " +
                        "If the user asks about a dog, cat, or other subject, check if you have a matching image description before responding. " +
                        "If a user tells you something, and they ask about it, just answer. Don't say its that its not in the uploads")
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory), // CHAT MEMORY
                        new QuestionAnswerAdvisor(vectorStore)) // RAG

                .build();
    }

    public String chat(UUID userId, String userMessageContent) {
        // Update last access time for this user
        lastAccessTimes.put(userId, LocalDateTime.now());

        // Create a consistent conversationId using the userId
        String conversationId = userId.toString();

        return this.chatClient.prompt()
                .user(userMessageContent)
                .advisors(a -> a
                        // This is the key part - providing the conversation ID to the advisor
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100))
                .call().content();
    }


}
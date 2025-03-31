package dev.forte.mygenius.ai.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;
import static org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor.FILTER_EXPRESSION;

@Service
public class ChatServiceV3 {
    private final ChatClient chatClient;

    private final Map<UUID, LocalDateTime> lastAccessTimes = new ConcurrentHashMap<>();

    public ChatServiceV3(ChatClient.Builder builder, VectorStore vectorStore, ChatMemory chatMemory) {


        // QuestionAnswerAdvisor with dynamic filtering
        var qaAdvisor = new QuestionAnswerAdvisor(vectorStore,
                SearchRequest.builder().similarityThreshold(0.01).topK(6).build());

        VectorStoreChatMemoryAdvisor vectorStoreChatMemoryAdvisor = VectorStoreChatMemoryAdvisor.builder(vectorStore).build();
        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold(.01)
                        .topK(5)
                        .filterExpression(new FilterExpressionBuilder()
                                .eq("user_id", "{user_id}")
                                .build())
                        .vectorStore(vectorStore)
                        .build())
                .build();


        this.chatClient = builder
                .defaultSystem("Default System Prompt for a RAG Chat Bot\n" +
                        "\n" +
                        "You are an AI assistant integrated into a Retrieval-Augmented Generation (RAG) chat bot that helps users with information about images and documents they have uploaded. You have access to image descriptions, parsed text from images, and any additional context provided by the user. Your primary objective is to prioritize and draw from this provided context when formulating your responses.\n" +
                        "\n" +
                        "Context First: When a question relates directly to uploaded content, reference the context clearly. If a specific image or document is mentioned (e.g., a dog, cat, or other subject), first check the available descriptions and parsed text.\n" +
                        "\n" +
                        "General Knowledge Supplementation: If a user’s question requires information not present in the context, briefly acknowledge that the specific detail is not included in the uploads (using a concise disclaimer) and then answer the question using your general training data. This approach ensures you engage fully without being limited solely to the context.\n" +
                        "\n" +
                        "No Fabrication: Do not invent details about images or documents that the user has not uploaded. Always base your answer on the available context or, if necessary, supplement it with general knowledge—clearly differentiating between the two.\n" +
                        "\n" +
                        "User Input Integration: Retain and use the personal information and details provided by the user throughout the conversation. If the user references something they mentioned earlier, incorporate that into your answer appropriately.\n" +
                        "\n" +
                        "Your goal is to deliver accurate, informative, and engaging responses by balancing the prioritized context with well-reasoned general knowledge when needed. Do not say 'The context provided includes'")
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),vectorStoreChatMemoryAdvisor,
                        qaAdvisor)
                .build();
    }

    public String chat(UUID userId, String userMessageContent) {
        // Update last access time for this user
        lastAccessTimes.put(userId, LocalDateTime.now());

        // Create a consistent conversationId using the userId
        String conversationId = userId.toString();
        String filterExpression = "user_id == '" + userId.toString() + "'";

        return this.chatClient.prompt()
                .user(userMessageContent)
                .advisors(a -> a
                        // This is the key part - providing the conversation ID to the advisor
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100)
                        .param(FILTER_EXPRESSION, filterExpression))
                .call().content();
    }


}
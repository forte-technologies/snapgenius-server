
use or download the app at https://mygenius.netlify.app/

### Image Upload and Processing

1.  Authenticated user uploads an image file via a POST request to `/api/user/images`.
2.  `ImageController` receives the request.
3.  `JwtAuthenticationFilter` authenticates the user.
4.  `ImageController` passes the user ID and `MultipartFile` to `ImageProcessingService.processUserImage`.
5.  `ImageProcessingService`:
    *   Saves image metadata (`Image`) to the database.
    *   Saves image binary data (`ImageContent`) to the database with status 'pending'.
    *   Calls the OpenAI Vision model (`OpenAiChatModel`) with the image data to get a description/parsed text.
    *   Updates the `ImageContent` record with the description and status 'completed'.
    *   Creates a Spring AI `Document` containing the description and user/content metadata.
    *   Adds the `Document` to the `PgVectorStore`, which generates and stores the embedding.
6.  `ImageController` returns a success response.

### RAG Chat

1.  Authenticated user sends a message via POST to `/api/user/chat/rag` or `/api/user/chat/ragStream`.
2.  `ChatController` receives the request.
3.  `JwtAuthenticationFilter` authenticates the user.
4.  `ChatController` calls `ChatServiceV3.chat` or `ChatServiceV3.chatStream` with the user ID and message.
5.  `ChatServiceV3` uses Spring AI `ChatClient` configured with:
    *   A system prompt guiding the AI.
    *   `VectorStoreRetriever`: Queries the `PgVectorStore` for relevant document chunks (image descriptions) based on the user's message, **filtered by the user's ID** (ensuring data isolation).
    *   Potentially other advisors (like chat memory).
6.  The retrieved document chunks are added as context to the prompt sent to the underlying chat model (e.g., OpenAI GPT).
7.  The AI generates a response based on the user's query and the retrieved context.
8.  For `/rag`, the complete response `String` is returned.
9.  For `/ragStream`, a `Flux<String>` (using `chatClient...stream().content()`) is returned, streaming response chunks.
10. `ChatController` returns the response/stream to the frontend.

## Configuration

Key configuration is managed in `src/main/resources/application.properties` and profile-specific files like `application-dev.properties`.
Important properties include:

*   Database connection details (`spring.datasource.*`)
*   OpenAI API Key (`spring.ai.openai.api-key`)
*   OpenAI Chat/Vision model names (`spring.ai.openai.chat.options.*`)
*   Vector Store configuration (`spring.ai.vectorstore.pgvector.*`)
*   JWT secret and expiration (`jwt.secret`, `jwt.expiration`)
*   Allowed CORS origins (`allowed.origins`)
*   Frontend application URL for OAuth redirects (`app.frontend.url`)
*   Virtual Thread enablement (`spring.threads.virtual.enabled=true`)

## Setup and Running

1.  **Prerequisites:**
    *   Java 21 JDK
    *   Maven
    *   PostgreSQL database instance with the `pgvector` extension enabled.
    *   OpenAI API Key.
2.  **Configuration:**
    *   Create or update `application.properties` or a profile-specific file (e.g., `application-dev.properties`) with your specific database credentials, OpenAI key, JWT secret, frontend URL, and allowed origins.
3.  **Build:**
    ```bash
    ./mvnw clean package
    ```
4.  **Run:**
    ```bash
    java -jar target/mygenius-*.jar --spring.profiles.active=dev # Or your active profile
    ```
    Alternatively, run directly using Maven:
    ```bash
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev # Or your active profile
    ```

The application should start on the configured port (default 8080, check properties). Ensure the database is accessible and the `pgvector` extension is correctly installed.

## API Endpoints

*   `GET /api/auth/me`: Get current authenticated user details.
*   `POST /api/user/images`: Upload an image (requires `multipart/form-data`, `file` part).
*   `POST /api/user/chat/rag`: Send a chat message for RAG response (Request Body: `{\"message\": \"Your query\"}`).
*   `POST /api/user/chat/ragStream`: Send a chat message for streaming RAG response (Request Body: `{\"message\": \"Your query\"}`).
*   `/oauth2/authorization/google`: (Handled by Spring Security) Initiates Google login.

*(Note: Public/error paths like `/`, `/error`, `/public/**` might also be configured)*

---
# Security Context Propagation for Streaming in Spring MVC

## Overview

Our application implements streaming responses in a traditional Spring Web MVC application without migrating to a full WebFlux reactive stack. This approach allows us to leverage reactive types like `Flux` for streaming while maintaining our existing Spring MVC architecture.

The key technical challenge we solved was propagating the security context across asynchronous boundaries in the streaming response pipeline, ensuring authenticated users maintain their security context throughout long-lived streaming connections.

## Implementation

### 1. Security Context Thread Local Accessor

We created a custom `ThreadLocalAccessor` implementation to make the Spring Security context available for propagation across thread boundaries:

```java
public class SecurityContextThreadLocalAccessor implements ThreadLocalAccessor<SecurityContext> {
    public static final String KEY = "org.springframework.security.context";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public SecurityContext getValue() {
        return SecurityContextHolder.getContext();
    }

    @Override
    public void setValue(SecurityContext value) {
        SecurityContextHolder.setContext(value);
    }

    @Override
    public void reset() {
        SecurityContextHolder.clearContext();
    }
}
```

### 2. Configuration

We registered this accessor as a Spring bean:

```java
@Configuration
public class SecurityContextPropagationConfig {
    @Bean
    public ThreadLocalAccessor<SecurityContext> securityContextAccessor() {
        return new SecurityContextThreadLocalAccessor();
    }
}
```

And configured Spring Security to not require explicit saving of the security context:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // other configuration...
        .securityContext(s -> s.requireExplicitSave(false));
    
    return http.build();
}
```

### 3. Async Support with Virtual Threads

We configured Spring MVC to use virtual threads for asynchronous request processing, leveraging Java 21's virtual thread capabilities:

```java
@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(30000);
        configurer.setTaskExecutor(mvcTaskExecutor());
    }

    @Bean
    public AsyncTaskExecutor mvcTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
```

We also enabled virtual threads in `application.properties`:

```properties
spring.threads.virtual.enabled=true
```

### 4. Chat Service Implementation

The `ChatServiceV3` class registers our security context accessor with the Micrometer `ContextRegistry` and uses a `ContextSnapshot` to propagate the security context across reactive streams:

```java
@Service
public class ChatServiceV3 {
    
    public ChatServiceV3(ChatClient.Builder builder, VectorStore vectorStore, 
                         ChatMemory chatMemory,
                         ThreadLocalAccessor<SecurityContext> securityContextAccessor) {
        
        // Register the security context accessor
        ContextRegistry contextRegistry = ContextRegistry.getInstance();
        contextRegistry.registerThreadLocalAccessor(securityContextAccessor);
        
        // Other initialization...
    }
    
    public Flux<String> chatStream(UUID userId, String userMessageContent) {
        // Other implementation...
        
        // Capture the current security context
        ContextSnapshot snapshot = ContextSnapshot.captureAll();
        
        // Return a reactive stream with context propagation
        return this.chatClient.prompt()
                .user(userMessageContent)
                .advisors(/* ... */)
                .stream().content().contextWrite(snapshot::updateContext);
    }
}
```

### 5. Controller Implementation

Our controller method simply returns the `Flux<String>` directly, allowing Spring MVC's async support to handle the streaming:

```java
@PostMapping(value = "/stream")
public Flux<String> streamChat(
        @AuthenticationPrincipal CustomUserPrincipal userPrincipal,
        @RequestBody Map<String, String> request) {
    UUID userId = userPrincipal.getUserId();
    String message = request.get("message");
    return chatServiceV3.chatStream(userId, message);
}
```

## How It Works

1. When a request arrives, Spring Security authenticates the user and establishes the security context in `SecurityContextHolder`
2. Our controller extracts the user ID from the authenticated principal and calls the service
3. The service captures the current security context with `ContextSnapshot.captureAll()`
4. As the chat response streams back to the client, the reactive framework may switch between threads
5. Each time a thread switch occurs, the `contextWrite(snapshot::updateContext)` operator restores the security context
6. This ensures that all operations in the reactive pipeline have access to the authenticated user's security context

This approach allows us to implement streaming responses within Spring MVC while maintaining proper security context throughout the entire streaming process.

## Benefits

- Maintains compatibility with existing Spring MVC architecture
- Leverages virtual threads for efficient handling of many concurrent connections
- Properly propagates security context across asynchronous boundaries
- Provides streaming capabilities without migrating to a full reactive stack
- Scales efficiently for handling many concurrent chat sessions


## Further Development

*   **Refine Error Handling:** Implement more specific exception handling and user-friendly error responses.
*   **Improve Blocking Calls:** Evaluate blocking calls (e.g., JPA repository access, `vectorStore.add`) within reactive streams or async processing; consider alternatives if performance becomes an issue.
*   **Testing:** Add comprehensive unit and integration tests.
*   **Scalability:** Evaluate performance under load and optimize database queries, AI calls, and thread management.

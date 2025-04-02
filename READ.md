
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

## Further Development

*   **Refine Error Handling:** Implement more specific exception handling and user-friendly error responses.
*   **Improve Blocking Calls:** Evaluate blocking calls (e.g., JPA repository access, `vectorStore.add`) within reactive streams or async processing; consider alternatives if performance becomes an issue.
*   **Testing:** Add comprehensive unit and integration tests.
*   **Scalability:** Evaluate performance under load and optimize database queries, AI calls, and thread management.

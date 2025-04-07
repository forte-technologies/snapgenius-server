
SnapGenius is a Progressive Web Application (PWA) that empowers users to upload unlimited images and documents. These uploads are converted into vector embeddings using OpenAI's embedding model, creating a searchable knowledge base. The application features a retrieval-augmented generation chatbot. When users submit queries, the backend performs a similarity search against their uploaded content, retrieving the 5-6 most relevant documents or images as context for generating accurate, personalized responses. This approach allows users to effectively interact with large volumes of their own data, as the system intelligently selects only the most relevant information needed to answer each specific query.

Use or download the app at https://snapgenius.app


This repository contains the backend for the snapGenius application. It handles core business logic, data persistence, and interaction with AI models.

**Core Functionalities:**

*   **Authentication:** Manages user authentication via Google OAuth 2.0, issuing JWT tokens for secure API access.
*   **Image Processing:**
    *   Accepts image uploads from authenticated users.
    *   Stores image metadata and binary content in a PostgreSQL database.
    *   Utilizes the OpenAI Vision API to analyze images:
        *   Generates detailed descriptions for images without text.
        *   Extracts text verbatim from images containing text.
    *   Saves the generated description or extracted text associated with the image.
*   **Vector Embeddings:**
    *   Generates vector embeddings for the image descriptions/text using OpenAI's embedding models.
    *   Stores these embeddings in a PgVectorStore database, enabling semantic search capabilities.
*   **API Endpoints:** Provides RESTful APIs for:
    *   User authentication and profile management.
    *   Image uploading and retrieval (including paginated lists of file names).
    *   Endpoints to support the Retrieval-Augmented Generation (RAG) chat functionality handled by a separate microservice (providing necessary data like user tokens or handling specific RAG requests).

**Database Schema:**

*   Uses PostgreSQL with the PgVector extension.
*   Tables store user information, image metadata (`images`), image binary content (`image_content`), and potentially other application data. Vector embeddings are managed within the PgVectorStore.

## Upcoming Features

The backend is planned to be extended with the following capabilities:

*   **Document Uploads:** Support for uploading various document types (e.g., PDF, DOCX), extracting their text content, generating embeddings, and making them searchable via the chat interface.
*   **Upload Deletion:** Functionality for users to delete their uploaded images and documents, ensuring removal from both metadata/content storage and the vector database.
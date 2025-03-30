package dev.forte.mygenius.ai;

import dev.forte.mygenius.ai.chat.ChatService;
import dev.forte.mygenius.ai.chat.ChatServiceV2;
import dev.forte.mygenius.ai.chat.ChatServiceV3;
import dev.forte.mygenius.security.CustomUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user/chat")
public class ChatController {
    private final ChatService chatService;
    private final ChatServiceV2 chatServiceV2;
    private final ChatServiceV3 chatServiceV3;

    public ChatController(ChatService chatService, ChatServiceV2 chatServiceV2, ChatServiceV3 chatServiceV3) {
        this.chatService = chatService;
        this.chatServiceV2 = chatServiceV2;
        this.chatServiceV3 = chatServiceV3;
    }

    @PostMapping("/rag")
    public ResponseEntity<Map<String, Object>> ragChat(
            @AuthenticationPrincipal CustomUserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {

        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Message cannot be empty"));
        }

        try {
            String response = chatServiceV3.chat(userPrincipal.getUserId(), message);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("response", response);
            responseBody.put("timestamp", new Date());

            return ResponseEntity.ok(responseBody);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error generating response: " + e.getMessage()));
        }
    }
}
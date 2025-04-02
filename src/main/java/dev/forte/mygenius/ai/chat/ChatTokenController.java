package dev.forte.mygenius.ai.chat;

import dev.forte.mygenius.security.CustomUserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user/chat/token")
public class ChatTokenController {
    @Value("${chat.service.url}")
    private String chatServiceUrl;

    private final ChatTokenService chatTokenService;

    public ChatTokenController(ChatTokenService chatTokenService) {
        this.chatTokenService = chatTokenService;
    }

    @GetMapping("/token")
    public ResponseEntity<Map<String, String>> getChatToken(
            @AuthenticationPrincipal CustomUserPrincipal userPrincipal) {

        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        String token = chatTokenService.generateChatToken(userPrincipal.getUserId());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "chatServiceUrl", chatServiceUrl
        ));
    }
}
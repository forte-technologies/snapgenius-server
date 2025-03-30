package dev.forte.mygenius;

import dev.forte.mygenius.user.User;
import dev.forte.mygenius.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final UserService userService;

    public TestController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/auth-check")
    public ResponseEntity<Map<String, Object>> checkAuthentication(HttpServletRequest request) {
        // Get the current authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getName();

        // Get the userId stored by JwtAuthenticationFilter
        UUID userId = (UUID) request.getAttribute("userId");

        // Get full user from database
        User user = userService.findByEmail(userEmail);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Authentication successful!");
        response.put("email", userEmail);
        response.put("userId", userId);
        response.put("username", user.getUsername());
        response.put("timestamp", new Date());
        response.put("isAuthenticated", authentication.isAuthenticated());

        return ResponseEntity.ok(response);
    }
    /**
     * Echo endpoint - returns whatever data was sent
     */
    @PostMapping("/echo")
    public ResponseEntity<Map<String, Object>> echoData(@RequestBody Map<String, Object> data,
                                                        Authentication authentication) {
        // This demonstrates injecting Authentication directly as a parameter

        Map<String, Object> response = new HashMap<>(data);  // Copy the incoming data
        response.put("authenticated_user", authentication.getName());
        response.put("received_at", new Date());

        return ResponseEntity.ok(response);
    }

    /**
     * Test endpoint with path variable
     */
    @GetMapping("/items/{id}")
    public ResponseEntity<Map<String, Object>> getItem(@PathVariable String id) {
        // Access current user from SecurityContextHolder
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        Map<String, Object> response = new HashMap<>();
        response.put("item_id", id);
        response.put("requestedBy", userEmail);
        response.put("requestTime", new Date());

        return ResponseEntity.ok(response);
    }
}
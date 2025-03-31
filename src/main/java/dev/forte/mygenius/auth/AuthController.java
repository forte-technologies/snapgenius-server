package dev.forte.mygenius.auth;

import dev.forte.mygenius.security.CustomUserPrincipal;
import dev.forte.mygenius.security.JwtService;
import dev.forte.mygenius.user.User;
import dev.forte.mygenius.user.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /**
     * Get current authenticated user information
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal CustomUserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            // You can retrieve the user using the email from the custom principal
            User user = userService.findByEmail(userPrincipal.getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Authentication successful!");
            response.put("email", user.getEmail());
            response.put("userId", user.getUserId());
            response.put("username", user.getUsername());
            response.put("timestamp", new Date());
            response.put("isAuthenticated", true);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error retrieving user: " + e.getMessage()));
        }
    }

    /**
     * Endpoint to validate a token and return user information
     * This can be used by the frontend to validate tokens stored in sessionStorage
     */
    @PostMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody Map<String, String> requestBody) {
        String token = requestBody.get("token");
        
        if (token == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Token is required"));
        }

        try {
            // Parse the token to get email and userId
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtService.getJwtSecret().getBytes()))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String email = claims.getSubject();
            UUID userId = UUID.fromString(claims.get("userId", String.class));
            
            // Get user details
            User user = userService.findByEmail(email);
            
            Map<String, Object> response = new HashMap<>();
            response.put("email", user.getEmail());
            response.put("userId", user.getUserId());
            response.put("username", user.getUsername());
            response.put("isAuthenticated", true);
            
            // Check if token is about to expire (less than 30 minutes left)
            Date expiration = claims.getExpiration();
            long expiresInMs = expiration.getTime() - System.currentTimeMillis();
            
            if (expiresInMs < 1800000) { // Less than 30 minutes
                // Generate a fresh token
                String newToken = jwtService.generateToken(email, userId);
                response.put("token", newToken);
                response.put("tokenRefreshed", true);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token expired"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid token: " + e.getMessage()));
        }
    }

    /**
     * Logout endpoint - clears the JWT cookie
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse response) {
        // Create an expired cookie to clear the JWT
        ResponseCookie cookie = ResponseCookie.from("JWT_TOKEN", "")
                .httpOnly(true)
                .secure(true) // Set to true in production with HTTPS
                .path("/")
                .maxAge(0) // Expired cookie
                .sameSite("None")
                .build();

        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(Map.of("message", "Successfully logged out"));
    }

    /**
     * Refresh JWT token (optional, for extending sessions)
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(HttpServletRequest request,
                                          HttpServletResponse response) {
        // Get existing token from cookies
        String token = null;
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("JWT_TOKEN".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No token to refresh"));
        }

        try {
            // Parse the token to get email and userId
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtService.getJwtSecret().getBytes()))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Extract values from the token
            String email = claims.getSubject();
            UUID userId = UUID.fromString(claims.get("userId", String.class));

            // Check if token is still valid (not expired)
            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.after(new Date())) {
                // Generate new token
                String newToken = jwtService.generateToken(email, userId);

                // Set the new token as a cookie
                ResponseCookie tokenCookie = ResponseCookie.from("JWT_TOKEN", newToken)
                        .httpOnly(true)
                        .secure(true) // Set to true in production
                        .path("/")
                        .maxAge(86400) // 1 day
                        .sameSite("None")
                        .build();

                response.setHeader(HttpHeaders.SET_COOKIE, tokenCookie.toString());

                return ResponseEntity.ok(Map.of("message", "Token refreshed successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Token expired"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Error refreshing token: " + e.getMessage()));
        }
    }
}
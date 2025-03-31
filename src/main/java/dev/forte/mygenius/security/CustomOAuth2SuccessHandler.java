package dev.forte.mygenius.security;

import dev.forte.mygenius.user.User;
import dev.forte.mygenius.user.UserService;
import jakarta.servlet.ServletException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


// Updated CustomOAuth2SuccessHandler
@Component
public class CustomOAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    // Store for temporary auth codes (in production, use Redis or another distributed store)
    private static final Map<String, String> tempAuthCodes = new ConcurrentHashMap<>();
    
    // Code expiration time (5 minutes)
    private static final long CODE_EXPIRATION_MS = 5 * 60 * 1000;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final JwtService jwtService;
    private final UserService userService;

    public CustomOAuth2SuccessHandler(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }
    
    /**
     * Store a token with a temporary code
     */
    public static String storeTokenWithCode(String token) {
        // Generate a random code
        String code = UUID.randomUUID().toString();
        
        // Store the token with the code
        tempAuthCodes.put(code, token);
        
        // Schedule code removal after expiration (simplified implementation)
        new Thread(() -> {
            try {
                Thread.sleep(CODE_EXPIRATION_MS);
                tempAuthCodes.remove(code);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        return code;
    }
    
    /**
     * Get and remove a token using its code (one-time use)
     */
    public static String getAndRemoveToken(String code) {
        return tempAuthCodes.remove(code);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        // Save or update user in the database
        User user = userService.findOrCreateGoogleUser(oauthUser);

        // Generate JWT with user ID
        String token = jwtService.generateToken(user.getEmail(), user.getUserId());

        // Create secure HTTP-only cookie with SameSite attribute
        ResponseCookie tokenCookie = ResponseCookie.from("JWT_TOKEN", token)
                .httpOnly(true)
                .secure(true) // true in production
                .path("/")
                .maxAge(86400) // 1 day in seconds
                .sameSite("None")
                .build();

        response.setHeader(HttpHeaders.SET_COOKIE, tokenCookie.toString());
        
        // Add token as Authorization header for clients that support it
        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        // Generate a one-time code instead of passing the token directly
        String authCode = storeTokenWithCode(token);
        
        // Redirect with the auth code instead of the actual token
        String dashboardUrl = frontendUrl + "/dashboard?code=" + authCode;
        getRedirectStrategy().sendRedirect(request, response, dashboardUrl);
    }
}
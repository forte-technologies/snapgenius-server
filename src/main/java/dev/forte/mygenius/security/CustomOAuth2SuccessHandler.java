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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


// Updated CustomOAuth2SuccessHandler
@Component
public class CustomOAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final JwtService jwtService;
    private final UserService userService;

    public CustomOAuth2SuccessHandler(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        // Save or update user in the database
        User user = userService.findOrCreateGoogleUser(oauthUser);

        // Generate JWT with user ID
        String token = jwtService.generateToken(user.getEmail(), user.getUserId());

        // Still set the HTTP-only cookie as a fallback for same-origin requests
        ResponseCookie tokenCookie = ResponseCookie.from("JWT_TOKEN", token)
                .httpOnly(true)
                .secure(true) 
                .path("/")
                .maxAge(86400) // 1 day in seconds
                .sameSite("None")
                .build();

        response.setHeader(HttpHeaders.SET_COOKIE, tokenCookie.toString());

        // Also include the token in the redirect URL for the frontend to capture
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String baseUrl = frontendUrl;
        String redirectUrl = baseUrl + "/auth-callback?token=" + encodedToken;
        
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
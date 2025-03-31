package dev.forte.mygenius.security;

import dev.forte.mygenius.user.User;
import dev.forte.mygenius.user.UserService;
import jakarta.servlet.ServletException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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

        // Redirect to dashboard with token as a hash fragment (no document.referrer reveals it)
        // Using hash fragments because they're not sent to the server
        String baseUrl = frontendUrl;
        String dashboardUrl = baseUrl + "/dashboard#auth_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8.toString());
        
        getRedirectStrategy().sendRedirect(request, response, dashboardUrl);
    }
}
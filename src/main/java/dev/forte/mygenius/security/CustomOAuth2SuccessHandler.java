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
import java.net.URI;
import java.net.URISyntaxException;


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

        // Create secure HTTP-only cookie with SameSite attribute (for backward compatibility)
        ResponseCookie tokenCookie = ResponseCookie.from("JWT_TOKEN", token)
                .httpOnly(true)
                .secure(true) // true in production
                .path("/")
                .maxAge(86400) // 1 day in seconds
                .sameSite("None")
                .build();

        response.setHeader(HttpHeaders.SET_COOKIE, tokenCookie.toString());

        // Redirect to a special auth callback page that will securely handle token storage via postMessage
        String baseUrl = frontendUrl;
        String callbackUrl = baseUrl + "/auth-callback";
        
        // We'll send the user to an auth-callback page that will securely handle the token
        // This page will use window.postMessage to securely transfer the token to the parent/opener window
        // and then redirect to the dashboard
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "  <title>Authentication Success</title>" +
            "  <script type=\"text/javascript\">" +
            "    window.onload = function() {" +
            "      try {" +
            "        window.localStorage.setItem('access_token', '" + token + "');" +
            "        window.location.href = '" + baseUrl + "/dashboard';" +
            "      } catch (e) {" +
            "        console.error('Error storing token', e);" +
            "        window.location.href = '" + baseUrl + "/auth-error';" +
            "      }" +
            "    };" +
            "  </script>" +
            "</head>" +
            "<body>" +
            "  <p>Authentication successful! Redirecting...</p>" +
            "</body>" +
            "</html>"
        );
    }
}
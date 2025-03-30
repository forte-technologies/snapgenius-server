package dev.forte.mygenius.user;// src/main/java/dev/forte/mygenius/service/UserService.java


import dev.forte.mygenius.user.UserRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;



import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User findOrCreateGoogleUser(OAuth2User oauthUser) {
        String oauthId = oauthUser.getAttribute("sub");
        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");

        // First try to find by OAuth ID
        Optional<User> existingUser = userRepository.findByOauthProviderAndOauthId("google", oauthId);

        if (existingUser.isPresent()) {
            return existingUser.get();
        }

        // Then try by email
        existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            // Update existing user with OAuth details
            User user = existingUser.get();
            user.setOauthProvider("google");
            user.setOauthId(oauthId);
            return userRepository.save(user);
        }

        // Create new user
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setUsername(generateUsername(name, email));
        newUser.setOauthProvider("google");
        newUser.setOauthId(oauthId);
        newUser.setCreatedAt(OffsetDateTime.now());

        return userRepository.save(newUser);
    }

    private String generateUsername(String name, String email) {
        // Generate a username based on name or email
        if (name != null && !name.trim().isEmpty()) {
            String username = name.toLowerCase().replaceAll("\\s+", "");

            // Check if username exists
            if (userRepository.findByUsername(username).isEmpty()) {
                return username;
            }
        }

        // Use part of email as username
        String emailUsername = email.split("@")[0];

        // Check if username exists
        if (userRepository.findByUsername(emailUsername).isEmpty()) {
            return emailUsername;
        }

        // Add random suffix to make unique
        return emailUsername + UUID.randomUUID().toString().substring(0, 8);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
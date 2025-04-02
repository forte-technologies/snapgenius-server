package dev.forte.mygenius.ai.chat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class ChatTokenService {

    @Value("${jwt.secret}")
    private String chatTokenSecret;

    @Value("${chat.token.expiration:1800}") // 30 minutes in seconds
    private int expirationTime;

    public String generateChatToken(UUID userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime * 1000);

        return Jwts.builder()
                .setSubject(userId.toString())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(Keys.hmacShaKeyFor(chatTokenSecret.getBytes()))
                .compact();
    }
}
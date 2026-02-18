package pt.dot.application.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public record JwtPrincipal(UUID userId, String role) {}

    private final SecretKey key;
    private final long ttlSeconds;

    public JwtService(
            @Value("${ptdot.jwt.secret:please-change-me-please-change-me-please-change-me-please}") String secret,
            @Value("${ptdot.jwt.ttl-seconds:1209600}") long ttlSeconds
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
    }

    public String generateToken(UUID userId, String role) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public JwtPrincipal tryParse(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String sub = c.getSubject();
            if (sub == null || sub.isBlank()) return null;

            UUID userId = UUID.fromString(sub);
            Object roleObj = c.get("role");
            String role = roleObj != null ? roleObj.toString() : "USER";

            return new JwtPrincipal(userId, role);
        } catch (Exception ignored) {
            return null;
        }
    }
}
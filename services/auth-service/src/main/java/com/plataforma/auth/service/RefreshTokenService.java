package com.plataforma.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String TOKEN_KEY_PREFIX = "refresh:token:";
    private static final String USER_KEY_PREFIX  = "refresh:user:";

    private final StringRedisTemplate redisTemplate;

    @Value("${refresh-token.ttl-days:7}")
    private long ttlDays;

    /**
     * Generates a new refresh token for the given user.
     * Stores two entries in Redis:
     *   refresh:token:{uuid}  → userId  (fast lookup)
     *   refresh:user:{userId} → Set of tokens  (bulk revocation)
     *
     * @param userId the authenticated user's id
     * @return the generated UUID token string
     */
    public String generate(Long userId) {
        String token = UUID.randomUUID().toString();
        Duration ttl = Duration.ofDays(ttlDays);

        String tokenKey = TOKEN_KEY_PREFIX + token;
        String userKey  = USER_KEY_PREFIX + userId;

        redisTemplate.opsForValue().set(tokenKey, String.valueOf(userId), ttl);
        redisTemplate.opsForSet().add(userKey, token);
        redisTemplate.expire(userKey, ttl);

        return token;
    }

    /**
     * Validates a refresh token and returns the associated userId if it exists.
     *
     * @param token the UUID refresh token
     * @return Optional containing the userId, or empty if the token is missing/expired
     */
    public Optional<Long> validate(String token) {
        String value = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }

    /**
     * Revokes a single refresh token.
     *
     * @param token the UUID refresh token to revoke
     */
    public void revoke(String token) {
        String value = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        if (value != null) {
            Long userId = Long.parseLong(value);
            redisTemplate.delete(TOKEN_KEY_PREFIX + token);
            redisTemplate.opsForSet().remove(USER_KEY_PREFIX + userId, token);
        }
    }

    /**
     * Revokes all refresh tokens belonging to a user.
     * Useful for forced logout from all devices.
     *
     * @param userId the user whose tokens should be revoked
     */
    public void revokeAllForUser(Long userId) {
        String userKey = USER_KEY_PREFIX + userId;
        Set<String> tokens = redisTemplate.opsForSet().members(userKey);
        if (tokens != null) {
            for (String token : tokens) {
                redisTemplate.delete(TOKEN_KEY_PREFIX + token);
            }
        }
        redisTemplate.delete(userKey);
    }
}

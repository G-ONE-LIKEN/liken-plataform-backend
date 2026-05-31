package com.plataforma.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private static final String SECRET = "test-jwt-secret-with-at-least-32-characters";

    private JwtUtils jwtUtils;
    private Key key;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils(SECRET);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Construye un JWT con el shape post-DD004: sub = userId, sin claims de role/permissions.
     */
    private String buildToken(Long userId, long expirationMs) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    @Test
    void validToken_returnsTrue() {
        String token = buildToken(1L, 3_600_000);
        assertThat(jwtUtils.validateToken(token)).isTrue();
    }

    @Test
    void expiredToken_returnsFalse() {
        String token = buildToken(1L, -1_000);
        assertThat(jwtUtils.validateToken(token)).isFalse();
    }

    @Test
    void tamperedToken_returnsFalse() {
        String token = buildToken(1L, 3_600_000);
        String tampered = token.substring(0, token.length() - 4) + "xxxx";
        assertThat(jwtUtils.validateToken(tampered)).isFalse();
    }

    @Test
    void randomString_returnsFalse() {
        assertThat(jwtUtils.validateToken("not.a.jwt")).isFalse();
    }

    @Test
    void getUserId_extractsCorrectly() {
        String token = buildToken(42L, 3_600_000);
        assertThat(jwtUtils.getUserId(token)).isEqualTo(42L);
    }
}

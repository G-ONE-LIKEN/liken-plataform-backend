package com.plataforma.gateway;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Tests del gateway tras DD004: el JWT solo lleva el userId como subject,
 * y el gateway consulta {@code GET /internal/users/{id}/context} a user-service
 * para resolver rol, permisos, tier y kycStatus. Esos valores se inyectan como
 * headers al request rutado al backend.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    private static final String SECRET =
            "test-jwt-secret-with-at-least-32-characters";

    private static final Long USER_ID = 99L;

    @RegisterExtension
    static WireMockExtension authWm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension usuariosWm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension proyectosWm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerBackendUrls(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> SECRET);
        registry.add("app.services.auth-url",      authWm::baseUrl);
        registry.add("app.services.usuarios-url",  usuariosWm::baseUrl);
        registry.add("app.services.proyectos-url", proyectosWm::baseUrl);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired(required = false)
    private ReactiveRedisConnectionFactory redisConnectionFactory;

    /**
     * Antes de cada test que use un JWT valido, stubear el endpoint que el gateway
     * va a consultar para resolver el contexto del usuario (DD004).
     */
    @BeforeEach
    void stubUserContext() {
        if (redisConnectionFactory != null) {
            redisConnectionFactory.getReactiveConnection().serverCommands().flushAll().block();
        }
        usuariosWm.stubFor(get(urlEqualTo("/internal/users/" + USER_ID + "/context"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "userId": 99,
                                  "role": "ADMIN",
                                  "permissions": ["project:read"],
                                  "tier": "BASIC",
                                  "kycStatus": "APPROVED"
                                }
                                """)));
    }

    // ── Sin token ────────────────────────────────────────────────────────────

    @Test
    void login_noToken_routesToAuthService() {
        authWm.stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":\"fake-jwt\"}")));

        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@b.com\",\"password\":\"pass\"}")
                .exchange()
                .expectStatus().isOk();

        authWm.verify(postRequestedFor(urlEqualTo("/api/auth/login")));
        proyectosWm.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void googleLogin_noToken_routesToAuthService() {
        authWm.stubFor(post(urlEqualTo("/api/auth/google"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":\"fake-jwt\"}")));

        webTestClient.post().uri("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"idToken\":\"fake-google-credential\"}")
                .exchange()
                .expectStatus().isOk();

        authWm.verify(postRequestedFor(urlEqualTo("/api/auth/google")));
        proyectosWm.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void protectedRoute_noToken_returns401() {
        webTestClient.get().uri("/api/users")
                .exchange()
                .expectStatus().isUnauthorized();

        usuariosWm.verify(0, getRequestedFor(urlEqualTo("/api/users")));
        proyectosWm.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void projectsRoute_noToken_routesToProyectosWithCorsHeaders() {
        proyectosWm.stubFor(get(urlEqualTo("/api/projects"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":[]}")));

        webTestClient.get().uri("/api/projects")
                .header(HttpHeaders.ORIGIN, "http://localhost:3001")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3001");

        proyectosWm.verify(getRequestedFor(urlEqualTo("/api/projects")));
    }

    @Test
    void projectsPreflight_returnsCorsHeaders() {
        webTestClient.options().uri("/api/projects")
                .header(HttpHeaders.ORIGIN, "http://localhost:3001")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3001");

        proyectosWm.verify(0, anyRequestedFor(anyUrl()));
    }

    // ── Con token valido ─────────────────────────────────────────────────────

    @Test
    void usersRoute_validToken_routesToUsuarios() {
        usuariosWm.stubFor(get(urlEqualTo("/api/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":[]}")));

        webTestClient.get().uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken())
                .exchange()
                .expectStatus().isOk();

        usuariosWm.verify(getRequestedFor(urlEqualTo("/api/users")));
        proyectosWm.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void projectsRoute_validToken_routesToProyectos() {
        proyectosWm.stubFor(get(urlEqualTo("/api/projects"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":[]}")));

        webTestClient.get().uri("/api/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken())
                .exchange()
                .expectStatus().isOk();

        proyectosWm.verify(getRequestedFor(urlEqualTo("/api/projects")));
    }

    @Test
    void gatewayAddsHeaders_toBackend() {
        usuariosWm.stubFor(get(urlPathEqualTo("/api/roles"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":[]}")));

        webTestClient.get().uri("/api/roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken())
                .exchange()
                .expectStatus().isOk();

        usuariosWm.verify(getRequestedFor(urlPathEqualTo("/api/roles"))
                .withHeader("X-User-Id",          equalTo("99"))
                .withHeader("X-User-Role",        equalTo("ADMIN"))
                .withHeader("X-User-Permissions", equalTo("project:read"))
                .withHeader("X-User-Tier",        equalTo("BASIC"))
                .withHeader("X-User-KycStatus",   equalTo("APPROVED")));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Tras DD004, el JWT solo contiene el userId en {@code subject}.
     * Role, permissions, tier y kycStatus se obtienen del endpoint
     * {@code /internal/users/{id}/context} (stubeado en {@link #stubUserContext()}).
     */
    private String buildToken() {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(String.valueOf(USER_ID))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }
}

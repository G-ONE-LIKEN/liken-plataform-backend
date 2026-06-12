package com.plataforma.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.gateway.model.UserContext;
import com.plataforma.gateway.security.JwtUtils;
import com.plataforma.gateway.service.UserContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    private JwtUtils jwtUtils;
    private UserContextService userContextService;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtils = mock(JwtUtils.class);
        userContextService = mock(UserContextService.class);
        filter = new JwtAuthFilter(jwtUtils, userContextService, new ObjectMapper());
    }

    // --- rutas públicas ---

    @Test
    void loginPath_noToken_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("POST", "/api/auth/login", null);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, ex -> { captured.set(ex); return Mono.empty(); }).block();

        assertThat(captured.get()).isNotNull();
        verifyNoInteractions(jwtUtils);
        verifyNoInteractions(userContextService);
    }

    @Test
    void registerRequest_noToken_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("POST", "/api/auth/register/request", null);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, ex -> { captured.set(ex); return Mono.empty(); }).block();

        assertThat(captured.get()).isNotNull();
        verifyNoInteractions(jwtUtils);
        verifyNoInteractions(userContextService);
    }

    @Test
    void emailVerificationRequest_noToken_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("POST", "/api/auth/email-verification/request", null);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, ex -> { captured.set(ex); return Mono.empty(); }).block();

        assertThat(captured.get()).isNotNull();
        verifyNoInteractions(jwtUtils);
        verifyNoInteractions(userContextService);
    }

    @Test
    void getProjects_noToken_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("GET", "/api/projects", null);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, ex -> { captured.set(ex); return Mono.empty(); }).block();

        assertThat(captured.get()).isNotNull();
        verifyNoInteractions(jwtUtils);
        verifyNoInteractions(userContextService);
    }

    @Test
    void getProjectDetail_noToken_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("GET", "/api/projects/42", null);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, ex -> { captured.set(ex); return Mono.empty(); }).block();

        assertThat(captured.get()).isNotNull();
        verifyNoInteractions(jwtUtils);
        verifyNoInteractions(userContextService);
    }

    @Test
    void getUsers_noToken_returns401() {
        MockServerWebExchange exchange = exchangeFor("GET", "/api/users", null);

        filter.filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getUsers_malformedHeader_returns401() {
        MockServerWebExchange exchange = exchangeFor("GET", "/api/users", "Token abc");

        filter.filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getUsers_invalidToken_returns401() {
        when(jwtUtils.validateToken("bad-token")).thenReturn(false);
        MockServerWebExchange exchange = exchangeFor("GET", "/api/users", "Bearer bad-token");

        filter.filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getUsers_validToken_addsUserHeaders() {
        when(jwtUtils.validateToken("good-token")).thenReturn(true);
        when(jwtUtils.getUserId("good-token")).thenReturn(7L);
        when(userContextService.getContext(7L)).thenReturn(Mono.just(new UserContext(
                7L, "ADMIN", List.of("project:read", "project:create"), "GOLD", "APPROVED", null
        )));

        MockServerWebExchange exchange = exchangeFor("GET", "/api/users", "Bearer good-token");
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, ex -> { captured.set(ex); return Mono.empty(); }).block();

        HttpHeaders headers = captured.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("7");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("ADMIN");
        assertThat(headers.getFirst("X-User-Permissions")).isEqualTo("project:read,project:create");
        assertThat(headers.getFirst("X-User-Tier")).isEqualTo("GOLD");
        assertThat(headers.getFirst("X-User-KycStatus")).isEqualTo("APPROVED");
    }

    @Test
    void getUsers_userNotFound_returns401() {
        when(jwtUtils.validateToken("good-token")).thenReturn(true);
        when(jwtUtils.getUserId("good-token")).thenReturn(99L);
        // 404 de user-service = el usuario del token no existe o está inactivo
        when(userContextService.getContext(99L)).thenReturn(Mono.error(
                WebClientResponseException.create(404, "Not Found", HttpHeaders.EMPTY, new byte[0], null)));

        MockServerWebExchange exchange = exchangeFor("GET", "/api/users", "Bearer good-token");

        filter.filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getUsers_userServiceUnavailable_returns503() {
        when(jwtUtils.validateToken("good-token")).thenReturn(true);
        when(jwtUtils.getUserId("good-token")).thenReturn(99L);
        // Timeout / breaker abierto / 5xx = falla de infraestructura, no del
        // usuario: el gateway responde 503, nunca un 401 engañoso (ADR-0023).
        when(userContextService.getContext(99L)).thenReturn(Mono.error(new RuntimeException("connection timed out")));

        MockServerWebExchange exchange = exchangeFor("GET", "/api/users", "Bearer good-token");

        filter.filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    // --- helpers ---

    private MockServerWebExchange exchangeFor(String method, String path, String authHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder = switch (method) {
            case "POST" -> MockServerHttpRequest.post(path);
            case "PUT"  -> MockServerHttpRequest.put(path);
            case "DELETE" -> MockServerHttpRequest.delete(path);
            default     -> MockServerHttpRequest.get(path);
        };
        if (authHeader != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private GatewayFilterChain chain() {
        return exchange -> Mono.empty();
    }
}

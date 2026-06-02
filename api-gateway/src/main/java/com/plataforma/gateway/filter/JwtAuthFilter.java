package com.plataforma.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.gateway.model.UserContext;
import com.plataforma.gateway.security.JwtUtils;
import com.plataforma.gateway.service.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtUtils jwtUtils;
    private final UserContextService userContextService;
    private final ObjectMapper objectMapper;

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/google",
            "/api/auth/refresh",
            "/api/auth/logout"
    );

    private static final Map<String, HttpMethod> PUBLIC_METHOD_PATHS = Map.of(
            "/api/users", HttpMethod.POST
    );

    private static final Pattern PUBLIC_PROJECT_DETAIL_PATH = Pattern.compile("^/api/projects/\\d+$");
    private static final Pattern PUBLIC_PROJECT_METRICS_PATH = Pattern.compile("^/api/projects/\\d+/metrics$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (isPublic(path, method)) {
            return chain.filter(exchange);
        }

        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            return rejectUnauthorized(exchange, "Token ausente");
        }
        if (!jwtUtils.validateToken(token)) {
            return rejectUnauthorized(exchange, "Token inválido o expirado");
        }

        Long userId = jwtUtils.getUserId(token);

        return userContextService.getContext(userId)
                .flatMap(ctx -> {
                    String permissions = String.join(",", ctx.getPermissions());
                    ServerHttpRequest.Builder reqBuilder = request.mutate()
                            .header("X-User-Id", String.valueOf(ctx.getUserId()))
                            .header("X-User-Role", ctx.getRole())
                            .header("X-User-Permissions", permissions)
                            .header("X-User-Tier", ctx.getTier())
                            .header("X-User-KycStatus", ctx.getKycStatus());
                    if (ctx.getDeveloperStatus() != null) {
                        reqBuilder.header("X-Developer-Status", ctx.getDeveloperStatus());
                    }
                    ServerHttpRequest mutated = reqBuilder.build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                .onErrorResume(e -> rejectUnauthorized(exchange, "Usuario no encontrado o inactivo"));
    }

    private boolean isPublic(String path, HttpMethod method) {
        if (method == HttpMethod.GET && isPublicProjectPath(path)) {
            return true;
        }
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        HttpMethod publicMethod = PUBLIC_METHOD_PATHS.get(path);
        return publicMethod != null && publicMethod.equals(method);
    }

    /**
     * Extrae el JWT en este orden:
     *   1) Header Authorization: Bearer ...
     *   2) Query param ?access_token=...   (para EventSource/SSE que no permite headers custom)
     *   3) Cookie liken_session_token       (fallback)
     */
    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        String queryToken = request.getQueryParams().getFirst("access_token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken;
        }
        org.springframework.http.HttpCookie cookie = request.getCookies().getFirst("liken_session_token");
        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }
        return null;
    }

    private boolean isPublicProjectPath(String path) {
        return "/api/projects".equals(path)
                || PUBLIC_PROJECT_DETAIL_PATH.matcher(path).matches()
                || PUBLIC_PROJECT_METRICS_PATH.matcher(path).matches();
    }

    private Mono<Void> rejectUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "success", false,
                "message", message
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"success\":false,\"message\":\"Unauthorized\"}".getBytes();
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

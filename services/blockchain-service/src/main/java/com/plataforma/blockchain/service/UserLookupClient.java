package com.plataforma.blockchain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Consulta a {@code user-service} para resolver {@code walletAddress → userId}.
 *
 * <p>El endpoint upstream es {@code GET /internal/users/by-wallet/{address}}
 * y devuelve {@code {"userId": <Long>}} o {@code 404} si la wallet no está
 * vinculada a ningún usuario.
 */
@Slf4j
@Service
public class UserLookupClient {

    private final RestTemplate restTemplate;
    private final String userServiceUrl;

    public UserLookupClient(
            RestTemplateBuilder builder,
            @Value("${services.user-service-url}") String userServiceUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.userServiceUrl = userServiceUrl;
    }

    /**
     * @return userId si hay vinculación; {@link Optional#empty()} si la wallet
     *         no está vinculada todavía. Eventos con userId vacío se publican
     *         igualmente — los consumers descartan con warning hasta que el
     *         usuario complete el vínculo.
     */
    @SuppressWarnings("unchecked")
    public Optional<Long> userIdForWallet(String walletAddress) {
        try {
            Map<String, Object> body = restTemplate.getForObject(
                    userServiceUrl + "/internal/users/by-wallet/{address}",
                    Map.class,
                    walletAddress);
            if (body == null || body.get("userId") == null) return Optional.empty();
            Object id = body.get("userId");
            return Optional.of(((Number) id).longValue());
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().equals(HttpStatusCode.valueOf(404))) {
                return Optional.empty();
            }
            log.warn("Lookup wallet→user falló para {}: {}", walletAddress, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Lookup wallet→user inesperado para {}: {}", walletAddress, ex.getMessage());
            return Optional.empty();
        }
    }

    public UserContext userContext(Long userId) {
        try {
            return restTemplate.getForObject(
                    userServiceUrl + "/internal/users/{id}/context",
                    UserContext.class,
                    userId);
        } catch (Exception ex) {
            throw new IllegalStateException("No pude obtener el contexto del usuario " + userId + ": " + ex.getMessage(), ex);
        }
    }

    public record UserContext(Long userId, String walletAddress, String role) {
    }
}

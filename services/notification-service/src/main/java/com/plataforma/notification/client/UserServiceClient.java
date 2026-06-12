package com.plataforma.notification.client;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Cliente HTTP simple hacia user-service para resolver audiencias de broadcast
 * y datos basicos del destinatario (email/nombre para los templates).
 *
 * Las llamadas internas viajan sin gateway, por eso usamos la URL directa.
 * Hay endpoints en user-service que solo aceptan headers de gateway, asi que
 * cuando falla el lookup simplemente devolvemos null/empty y la notificacion
 * in-app igual se persiste; el email queda sin destinatario y se omite.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    @Value("${user-service.base-url}")
    private String baseUrl;

    private RestClient restClient;

    private RestClient client() {
        if (restClient == null) {
            restClient = RestClient.builder().baseUrl(baseUrl).build();
        }
        return restClient;
    }

    public Optional<UserContact> findContact(Long userId) {
        try {
            UserContact contact = client().get()
                    .uri("/internal/users/{id}/contact", userId)
                    .retrieve()
                    .body(UserContact.class);
            return Optional.ofNullable(contact);
        } catch (Exception ex) {
            log.warn("No se pudo resolver contacto del usuario {}: {}", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    public List<Long> findUserIdsByAudience(String audience) {
        try {
            Long[] ids = client().get()
                    .uri("/internal/users/by-audience?audience={a}", audience)
                    .retrieve()
                    .body(Long[].class);
            return ids != null ? List.of(ids) : Collections.emptyList();
        } catch (Exception ex) {
            log.warn("No se pudo resolver audiencia {}: {}", audience, ex.getMessage());
            return Collections.emptyList();
        }
    }

    @Data
    public static class UserContact {
        private Long id;
        private String email;
        private String firstName;
        private String lastName;
        private String role;
    }
}

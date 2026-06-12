package com.plataforma.user.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de seguridad: GatewayHeaderAuthFilter + @PreAuthorize.
 *
 * La autenticacion se simula inyectando los headers X-User-Id / X-User-Role /
 * X-User-Permissions directamente, tal como lo hace el API Gateway en produccion (DD002).
 * No hay validacion de JWT en este servicio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("unit")
class SecurityUnitTest {

    @Autowired
    private MockMvc mockMvc;

    // Kafka excluido en profile test, pero UserContextEventProducer requiere KafkaTemplate.
    // Mock para que el contexto pueda cargar.
    @MockBean
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    // ── Helper ────────────────────────────────────────────────────────────────

    private static RequestPostProcessor gatewayAuth(Long userId, String roleName, String... permissions) {
        return request -> {
            request.addHeader("X-User-Id", userId.toString());
            request.addHeader("X-User-Role", roleName);
            request.addHeader("X-User-Permissions", String.join(",", permissions));
            return request;
        };
    }

    // ── 401 sin autenticacion ─────────────────────────────────────────────────

    @Test
    void debeDevolver401_sinHeadersDeGateway() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void debeDevolver401_bearerTokenIgnorado() throws Exception {
        // El Authorization header es ignorado: sin X-User-Id → no hay auth → 401
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer token-basura-ignorado"))
            .andExpect(status().isUnauthorized());
    }

    // ── Autorizacion por permiso ──────────────────────────────────────────────

    @Test
    void debeDevolver403_sinPermisoProjectDelete() throws Exception {
        mockMvc.perform(delete("/api/test-security/borrar")
                .with(gatewayAuth(1L, "DEVELOPER", "project:read")))
            .andExpect(status().isForbidden());
    }

    @Test
    void debeDevolver200_conPermisoProjectDelete() throws Exception {
        mockMvc.perform(delete("/api/test-security/borrar")
                .with(gatewayAuth(1L, "ADMIN", "project:delete")))
            .andExpect(status().isOk());
    }
}

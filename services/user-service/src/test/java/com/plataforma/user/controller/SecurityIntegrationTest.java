package com.plataforma.user.controller;

import com.plataforma.AbstractIntegrationTest;
import com.plataforma.rbac.constant.RoleConstants;
import com.plataforma.rbac.model.Role;
import com.plataforma.rbac.repository.RoleRepository;
import com.plataforma.user.model.User;
import com.plataforma.user.repository.UserRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de seguridad contra Postgres real.
 *
 * La autenticacion se simula con headers de gateway (DD002).
 * No se genera ni valida ningun JWT en este servicio.
 */
@Tag("integration")
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RequestPostProcessor authForRole(String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        String email = roleName.toLowerCase() + "+" + System.nanoTime() + "@mail.com";
        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("pass123"))
                .role(role)
                .active(true)
                .build());
        String permissions = role.getPermissions().stream()
                .map(p -> p.getName())
                .collect(Collectors.joining(","));
        return gatewayAuth(user.getId(), roleName, permissions);
    }

    private static RequestPostProcessor gatewayAuth(Long userId, String roleName, String permissions) {
        return request -> {
            request.addHeader("X-User-Id", userId.toString());
            request.addHeader("X-User-Role", roleName);
            request.addHeader("X-User-Permissions", permissions);
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
        // Sin X-User-Id, el Authorization header es ignorado → 401
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer token-basura-123"))
            .andExpect(status().isUnauthorized());
    }

    // ── Autorizacion por permiso ──────────────────────────────────────────────

    @Test
    void debeDevolver403_developerSinPermisoProjectDelete() throws Exception {
        mockMvc.perform(delete("/api/test-security/borrar")
                .with(authForRole(RoleConstants.DEVELOPER)))
            .andExpect(status().isForbidden());
    }

    @Test
    void debeDevolver200_adminConPermisoProjectDelete() throws Exception {
        mockMvc.perform(delete("/api/test-security/borrar")
                .with(authForRole(RoleConstants.ADMIN)))
            .andExpect(status().isOk());
    }
}

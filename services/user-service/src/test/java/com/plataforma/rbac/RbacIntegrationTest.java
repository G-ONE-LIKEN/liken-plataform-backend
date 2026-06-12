package com.plataforma.rbac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.AbstractIntegrationTest;
import com.plataforma.rbac.constant.RoleConstants;
import com.plataforma.rbac.model.Role;
import com.plataforma.rbac.repository.RoleRepository;
import com.plataforma.user.model.User;
import com.plataforma.user.repository.UserRepository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integracion RBAC contra Postgres real.
 *
 * La autenticacion se simula con headers de gateway (DD002).
 * No se genera ni valida ningun JWT en este servicio.
 */
@Tag("integration")
public class RbacIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // ── Auth helpers ──────────────────────────────────────────────────────────

    /**
     * Crea un usuario con el rol indicado y devuelve un RequestPostProcessor
     * que inyecta los headers de gateway correspondientes.
     */
    private RequestPostProcessor authForRole(String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        String email = roleName.toLowerCase() + "+" + System.nanoTime() + "@mail.com";
        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("pass123"))
                .role(role)
                .active(true)
                .build());
        return authForUser(user);
    }

    /**
     * Construye el RequestPostProcessor para un usuario ya persistido.
     * Recarga el rol desde el repositorio para asegurar que los permisos estén disponibles.
     */
    private RequestPostProcessor authForUser(User user) {
        Role role = roleRepository.findByName(user.getRole().getName()).orElseThrow();
        String permissions = role.getPermissions().stream()
                .map(p -> p.getName())
                .collect(Collectors.joining(","));
        return gatewayAuth(user.getId(), role.getName(), permissions);
    }

    private static RequestPostProcessor gatewayAuth(Long userId, String roleName, String permissions) {
        return request -> {
            request.addHeader("X-User-Id", userId.toString());
            request.addHeader("X-User-Role", roleName);
            request.addHeader("X-User-Permissions", permissions);
            return request;
        };
    }

    // ── DB helpers ────────────────────────────────────────────────────────────

    private User createAdmin() {
        Role adminRole = roleRepository.findByName(RoleConstants.ADMIN).orElseThrow();
        return userRepository.save(User.builder()
                .email("admin+" + System.nanoTime() + "@mail.com")
                .password(passwordEncoder.encode("test-admin-password"))
                .role(adminRole)
                .active(true)
                .build());
    }

    private Long createUserAndGetId(String email, String password) throws Exception {
        String body = String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password);
        MvcResult result = mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        Map<String, Object> json = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) json.get("data");
        return Long.valueOf(data.get("id").toString());
    }

    private Long createRoleAndGetId(RequestPostProcessor adminAuth) throws Exception {
        String body = "{\"name\":\"ROLE_TEST\",\"description\":\"test\"}";
        MvcResult result = mockMvc.perform(post("/api/roles")
                .with(adminAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        Map<String, Object> json = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) json.get("data");
        return Long.valueOf(data.get("id").toString());
    }

    // --- GRUPO 1: AUTH ---

    @Test
    void adminAuthIsValid() {
        // Verifica que la creacion de auth de gateway no lanza excepciones
        RequestPostProcessor auth = authForRole(RoleConstants.ADMIN);
        Assertions.assertNotNull(auth);
    }

    // --- GRUPO 2: USUARIOS ---

    @Test
    @DisplayName("Usuarios: Crear usuario BASIC")
    void createUser() throws Exception {
        String body = "{\"email\": \"juan@mail.com\", \"password\": \"1234\"}";
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.email").value("juan@mail.com"));
    }

    @Test
    @DisplayName("Usuarios: Listar paginado")
    void listUsers() throws Exception {
        mockMvc.perform(get("/api/users")
                .with(authForRole(RoleConstants.ADMIN))
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Usuarios: Detalle usuario")
    void userDetail() throws Exception {
        Long userId = createUserAndGetId("user+" + System.nanoTime() + "@mail.com", "1234");
        mockMvc.perform(get("/api/users/" + userId)
                .with(authForRole(RoleConstants.ADMIN)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Usuarios: Editar usuario")
    void editUser() throws Exception {
        Long userId = createUserAndGetId("user+" + System.nanoTime() + "@mail.com", "1234");
        String body = "{\"email\": \"nuevo@mail.com\"}";
        mockMvc.perform(put("/api/users/" + userId)
                .with(authForRole(RoleConstants.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Usuarios: Asignar rol DEVELOPER")
    void assignRole() throws Exception {
        Long userId = createUserAndGetId("user+" + System.nanoTime() + "@mail.com", "1234");
        Role devRole = roleRepository.findByName(RoleConstants.DEVELOPER).orElseThrow();
        String body = "{\"roleId\": " + devRole.getId() + "}";
        mockMvc.perform(put("/api/users/" + userId + "/role")
                .with(authForRole(RoleConstants.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Usuarios: Soft Delete")
    void deleteUser() throws Exception {
        Long userId = createUserAndGetId("user+" + System.nanoTime() + "@mail.com", "1234");
        mockMvc.perform(delete("/api/users/" + userId)
                .with(authForRole(RoleConstants.ADMIN)))
            .andExpect(status().isOk());
    }

    // --- GRUPO 3: ROLES ---

    @Test
    @DisplayName("Roles: Listar roles con permisos")
    void listRoles() throws Exception {
        mockMvc.perform(get("/api/roles")
                .with(authForRole(RoleConstants.ADMIN)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Roles: Crear nuevo rol")
    void createRole() throws Exception {
        String body = "{\"name\": \"MODERADOR\", \"description\": \"Modera contenido\"}";
        mockMvc.perform(post("/api/roles")
                .with(authForRole(RoleConstants.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Roles: Asignar permisos a rol")
    void assignPermissionsToRole() throws Exception {
        RequestPostProcessor adminAuth = authForRole(RoleConstants.ADMIN);
        Long roleId = createRoleAndGetId(adminAuth);
        String body = "[1, 2]";
        mockMvc.perform(put("/api/roles/" + roleId + "/permissions")
                .with(adminAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }

    // --- GRUPO 4: PERMISOS ---

    @Test
    @DisplayName("Permisos: Crear permiso")
    void createPermission() throws Exception {
        String body = "{\"name\": \"report:read\", \"description\": \"Ver reportes\"}";
        mockMvc.perform(post("/api/permissions")
                .with(authForRole(RoleConstants.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
    }

    // --- GRUPO 5: AUTORIZACIoN (CASOS DE ERROR) ---

    @Test
    @DisplayName("Error: 401 Sin headers de gateway")
    void error401NoAuth() throws Exception {
        mockMvc.perform(get("/api/roles"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Error: 401 Bearer token ignorado (modelo gateway-auth)")
    void error401BearerIgnorado() throws Exception {
        // El Authorization header es ignorado; sin X-User-Id → 401
        mockMvc.perform(get("/api/roles")
                .header("Authorization", "Bearer token-falso"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Error: 403 Usuario BASIC intenta borrar rol")
    void basicCannotDeleteRole() throws Exception {
        Role adminRole = roleRepository.findByName(RoleConstants.ADMIN).orElseThrow();
        mockMvc.perform(delete("/api/roles/" + adminRole.getId())
                .with(authForRole(RoleConstants.BASIC)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Error: Admin no puede demotear a otro Admin")
    void errorAdminDemote() throws Exception {
        User admin1 = createAdmin();
        User admin2 = createAdmin();

        String body = "{\"roleId\": 2}";

        mockMvc.perform(put("/api/users/" + admin1.getId() + "/role")
                .with(authForUser(admin2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteRole() throws Exception {
        mockMvc.perform(delete("/api/roles/1")
                .with(authForRole(RoleConstants.ADMIN)))
            .andExpect(status().isOk());
    }
}

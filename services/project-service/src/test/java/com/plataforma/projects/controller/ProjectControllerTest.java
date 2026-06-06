package com.plataforma.projects.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.plataforma.projects.dto.ProjectRequest;
import com.plataforma.projects.dto.ProjectResponse;
import com.plataforma.projects.exception.GlobalExceptionHandler;
import com.plataforma.projects.exception.ProjectNotFoundException;
import com.plataforma.projects.exception.ProjectStateException;
import com.plataforma.projects.model.EnergyType;
import com.plataforma.projects.model.ProjectState;
import com.plataforma.projects.security.GatewayHeaderAuthFilter;
import com.plataforma.projects.service.ProjectMetricService;
import com.plataforma.projects.service.ProjectService;
import com.plataforma.projects.service.UserHoldingService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("unit")
@WebMvcTest(controllers = {ProjectController.class, ProjectMetricController.class})
@Import({ProjectControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.frontend-url=http://localhost:3000")
class ProjectControllerTest {

    /**
     * Seguridad mínima para el slice: permite todo a nivel URL, habilita
     * method security para que @PreAuthorize se evalúe con la auth inyectada,
     * y tiene @Order(1) para tomar precedencia sobre la cadena de producción.
     */
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        @Order(1)
        SecurityFilterChain testChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired MockMvc mockMvc;
    @MockBean ProjectService projectService;
    @MockBean UserHoldingService userHoldingService;
    @MockBean ProjectMetricService projectMetricService;
    // SecurityConfig (producción) requiere GatewayHeaderAuthFilter vía @RequiredArgsConstructor;
    // @WebMvcTest no lo escanea como @Component, así que hay que mockearlo.
    @MockBean GatewayHeaderAuthFilter gatewayHeaderAuthFilter;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // ── Helpers ────────────────────────────────────────────────────────────

    private static Authentication auth(Long userId, String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                userId, null,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }

    private ProjectResponse sampleResponse() {
        return ProjectResponse.builder()
                .id(1L).name("Solar Test").ownerId(1L)
                .state(ProjectState.DRAFT).energyType(EnergyType.SOLAR)
                .totalTokens(new BigDecimal("10000"))
                .earlyBirdPrice(new BigDecimal("8.0000"))
                .standardPrice(new BigDecimal("10.0000"))
                .build();
    }

    // ── GET /api/projects ──────────────────────────────────────────────────

    @Test
    void listProjects_sinAuth_returns200() throws Exception {
        when(projectService.listProjects(any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    void listProjects_conFiltroState_pasaFiltroAlServicio() throws Exception {
        when(projectService.listProjects(eq(ProjectState.OPEN), isNull(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/projects?state=OPEN"))
                .andExpect(status().isOk());
    }

    // ── GET /api/projects/{id} ─────────────────────────────────────────────

    @Test
    void getProject_existente_returns200ConData() throws Exception {
        when(projectService.getProject(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Solar Test"))
                .andExpect(jsonPath("$.data.state").value("DRAFT"));
    }

    @Test
    void getProject_noExiste_returns404() throws Exception {
        when(projectService.getProject(99L)).thenThrow(new ProjectNotFoundException(99L));

        mockMvc.perform(get("/api/projects/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── POST /api/projects ─────────────────────────────────────────────────

    @Test
    void createProject_conAuth_returns201() throws Exception {
        when(projectService.createProject(any(), eq(1L), anyBoolean())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/projects")
                        .with(authentication(auth(1L, "project:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Solar Test"));
    }

    // ── PUT /api/projects/{id}/state ───────────────────────────────────────

    @Test
    void createProject_sinCamposObligatorios_returns400() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(authentication(auth(1L, "project:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createProject_energyTypeInvalido_returns400() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(authentication(auth(1L, "project:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Solar Test",
                                  "description": "Parque solar de prueba.",
                                  "energyType": "INVALID",
                                  "totalTokens": 10000,
                                  "earlyBirdPrice": 8.00,
                                  "standardPrice": 10.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void changeState_transicionValida_returns200() throws Exception {
        ProjectResponse updated = sampleResponse();
        updated.setState(ProjectState.PRE_OPEN);
        when(projectService.changeState(eq(1L), eq(ProjectState.PRE_OPEN), eq(1L), eq(false)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/projects/1/state")
                        .with(authentication(auth(1L, "project:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\": \"PRE_OPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("PRE_OPEN"));
    }

    @Test
    void changeState_transicionInvalida_returns409() throws Exception {
        when(projectService.changeState(any(), any(), any(), anyBoolean()))
                .thenThrow(new ProjectStateException("No se puede pasar de CLOSED a OPEN"));

        mockMvc.perform(put("/api/projects/1/state")
                        .with(authentication(auth(1L, "project:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\": \"OPEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ── DELETE /api/projects/{id} ──────────────────────────────────────────

    @Test
    void deleteProject_conAuth_returns200() throws Exception {
        mockMvc.perform(delete("/api/projects/1")
                        .with(authentication(auth(1L, "project:delete"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Proyecto eliminado exitosamente"));
    }

    @Test
    void deleteProject_estadoNoPermitido_returns409() throws Exception {
        when(projectService.changeState(any(), any(), any(), anyBoolean()))
                .thenThrow(new ProjectStateException("Solo se pueden eliminar proyectos en estado DRAFT"));

        // este test valida que el handler mapea ProjectStateException → 409
        mockMvc.perform(put("/api/projects/1/state")
                        .with(authentication(auth(1L, "project:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\": \"DRAFT\"}"))
                .andExpect(status().isConflict());
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private ProjectRequest projectRequest() {
        ProjectRequest req = new ProjectRequest();
        req.setName("Solar Test");
        req.setDescription("Parque solar de prueba.");
        req.setEnergyType(EnergyType.SOLAR);
        req.setTotalTokens(new BigDecimal("10000"));
        req.setEarlyBirdPrice(new BigDecimal("8.0000"));
        req.setStandardPrice(new BigDecimal("10.0000"));
        return req;
    }
}

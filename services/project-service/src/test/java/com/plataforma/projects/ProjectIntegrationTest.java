package com.plataforma.projects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.plataforma.projects.dto.ProjectRequest;
import com.plataforma.projects.dto.StateChangeRequest;
import com.google.cloud.storage.Storage;
import com.plataforma.projects.event.ProjectEventPublisher;
import com.plataforma.projects.model.EnergyType;
import com.plataforma.projects.model.ProjectState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración end-to-end: Controller → Service → Repository → H2.
 * Kafka y S3 están mockeados. Flyway deshabilitado; Hibernate crea el schema con ddl-auto=create-drop.
 *
 * Correr con: mvn test -Pintegration
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectIntegrationTest {

    // La autenticación se inyecta directamente con authentication() (DD002).
    // GatewayHeaderAuthFilter no interfiere: sin X-User-Id header, pasa sin modificar el contexto.
    @MockBean ProjectEventPublisher eventPublisher;
    @MockBean Storage storage;

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // ── helpers ────────────────────────────────────────────────────────────

    /** Dev/owner — userId=1 */
    private static Authentication ownerAuth() {
        return new UsernamePasswordAuthenticationToken(
                1L, null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_DEV"),
                        new SimpleGrantedAuthority("project:create"),
                        new SimpleGrantedAuthority("project:read"),
                        new SimpleGrantedAuthority("project:update"),
                        new SimpleGrantedAuthority("project:delete")
                )
        );
    }

    /** Admin — userId=100 */
    private static Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                100L, null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("project:create"),
                        new SimpleGrantedAuthority("project:read"),
                        new SimpleGrantedAuthority("project:update"),
                        new SimpleGrantedAuthority("project:delete")
                )
        );
    }

    private ProjectRequest solarRequest() {
        ProjectRequest req = new ProjectRequest();
        req.setName("Campo Solar Córdoba");
        req.setDescription("Parque fotovoltaico en el Valle de Punilla, Córdoba.");
        req.setEnergyType(EnergyType.SOLAR);
        req.setProvince("Córdoba");
        req.setTotalTokens(new BigDecimal("50000"));
        req.setEarlyBirdPrice(new BigDecimal("10.0000"));
        req.setStandardPrice(new BigDecimal("12.5000"));
        req.setMinimumInvestment(new BigDecimal("100.00"));
        req.setExpectedAnnualYield(new BigDecimal("9.50"));
        return req;
    }

    // ── flujo completo ─────────────────────────────────────────────────────

    @Test
    void flujoCompleto_crearObtenerYCambiarEstado() throws Exception {
        // 1. Crear proyecto → debe iniciar en DRAFT
        String createJson = mockMvc.perform(post("/api/projects")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solarRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.state").value("DRAFT"))
                .andExpect(jsonPath("$.data.name").value("Campo Solar Córdoba"))
                .andReturn().getResponse().getContentAsString();

        Long projectId = objectMapper.readTree(createJson)
                .path("data").path("id").asLong();

        // 2. Obtener el proyecto creado
        mockMvc.perform(get("/api/projects/" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(projectId))
                .andExpect(jsonPath("$.data.energyType").value("SOLAR"));

        // 3. Avanzar estado: DRAFT → PRE_OPEN
        StateChangeRequest stateReq = new StateChangeRequest();
        stateReq.setState(ProjectState.PRE_OPEN);

        mockMvc.perform(put("/api/projects/" + projectId + "/state")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("PRE_OPEN"));

        // 4. PRE_OPEN → OPEN
        stateReq.setState(ProjectState.OPEN);
        mockMvc.perform(put("/api/projects/" + projectId + "/state")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("OPEN"));

        // 5. Se puede eliminar un proyecto OPEN (RF002.001.003: baja en cualquier estado)
        mockMvc.perform(delete("/api/projects/" + projectId)
                        .with(authentication(ownerAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void listProjects_sinAuth_returns200() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    void getProject_noExiste_returns404() throws Exception {
        mockMvc.perform(get("/api/projects/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createProject_sinAuth_returns4xx() throws Exception {
        // Sin auth → Spring Security devuelve 401 o 403 según la configuración del entry point
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solarRequest())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void changeState_transicionInvalida_returns409() throws Exception {
        // Crear un proyecto y dejarlo en DRAFT, luego intentar saltear a OPEN
        String createJson = mockMvc.perform(post("/api/projects")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solarRequest())))
                .andReturn().getResponse().getContentAsString();

        Long projectId = objectMapper.readTree(createJson).path("data").path("id").asLong();

        StateChangeRequest stateReq = new StateChangeRequest();
        stateReq.setState(ProjectState.OPEN); // salto inválido: DRAFT → OPEN

        mockMvc.perform(put("/api/projects/" + projectId + "/state")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stateReq)))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelProject_adminDesdeOpen_returns200() throws Exception {
        String createJson = mockMvc.perform(post("/api/projects")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solarRequest())))
                .andReturn().getResponse().getContentAsString();

        Long projectId = objectMapper.readTree(createJson).path("data").path("id").asLong();

        // Avanzar a OPEN como dev/owner
        for (ProjectState state : new ProjectState[]{ProjectState.PRE_OPEN, ProjectState.OPEN}) {
            StateChangeRequest req = new StateChangeRequest();
            req.setState(state);
            mockMvc.perform(put("/api/projects/" + projectId + "/state")
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        // Dev intenta cancelar → 403
        StateChangeRequest cancelReq = new StateChangeRequest();
        cancelReq.setState(ProjectState.CANCELLED);
        mockMvc.perform(put("/api/projects/" + projectId + "/state")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isForbidden());

        // Admin cancela → 200
        mockMvc.perform(put("/api/projects/" + projectId + "/state")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("CANCELLED"));
    }

    @Test
    void cancelProject_devDesdePreOpen_returns200() throws Exception {
        String createJson = mockMvc.perform(post("/api/projects")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solarRequest())))
                .andReturn().getResponse().getContentAsString();

        Long projectId = objectMapper.readTree(createJson).path("data").path("id").asLong();

        // Avanzar a PRE_OPEN
        StateChangeRequest preOpen = new StateChangeRequest();
        preOpen.setState(ProjectState.PRE_OPEN);
        mockMvc.perform(put("/api/projects/" + projectId + "/state")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(preOpen)))
                .andExpect(status().isOk());

        // Dev puede cancelar desde PRE_OPEN (sin inversores)
        StateChangeRequest cancelReq = new StateChangeRequest();
        cancelReq.setState(ProjectState.CANCELLED);
        mockMvc.perform(put("/api/projects/" + projectId + "/state")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("CANCELLED"));
    }

    @Test
    void createProject_sinDescripcion_returns400() throws Exception {
        ProjectRequest req = solarRequest();
        req.setDescription(null);

        mockMvc.perform(post("/api/projects")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProject_usuarioNoOwner_returns403() throws Exception {
        // Crear proyecto con owner = userId 1
        String createJson = mockMvc.perform(post("/api/projects")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solarRequest())))
                .andReturn().getResponse().getContentAsString();

        Long projectId = objectMapper.readTree(createJson).path("data").path("id").asLong();

        // Intentar actualizar con userId 99 (no es owner ni admin)
        Authentication otherUser = new UsernamePasswordAuthenticationToken(
                99L, null,
                List.of(new SimpleGrantedAuthority("project:update"))
        );

        mockMvc.perform(put("/api/projects/" + projectId)
                        .with(authentication(otherUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solarRequest())))
                .andExpect(status().isForbidden());
    }
}

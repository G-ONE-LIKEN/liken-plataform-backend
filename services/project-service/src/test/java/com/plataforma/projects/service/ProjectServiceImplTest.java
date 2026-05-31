package com.plataforma.projects.service;

import com.plataforma.projects.dto.ProjectRequest;
import com.plataforma.projects.dto.ProjectResponse;
import com.plataforma.projects.event.ProjectEventPublisher;
import com.plataforma.projects.exception.ProjectNotFoundException;
import com.plataforma.projects.exception.ProjectStateException;
import com.plataforma.projects.exception.UnauthorizedProjectAccessException;
import com.plataforma.projects.model.EnergyType;
import com.plataforma.projects.model.Project;
import com.plataforma.projects.model.ProjectState;
import com.plataforma.projects.repository.ProjectRepository;
import com.plataforma.projects.service.impl.ProjectServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock ProjectRepository projectRepository;
    @Mock ProjectEventPublisher eventPublisher;
    @InjectMocks ProjectServiceImpl projectService;

    private Project draftProject;

    @BeforeEach
    void setUp() {
        draftProject = Project.builder()
                .id(1L)
                .name("Solar Test")
                .ownerId(10L)
                .state(ProjectState.DRAFT)
                .energyType(EnergyType.SOLAR)
                .totalTokens(new BigDecimal("10000"))
                .tokenPrice(new BigDecimal("10.00"))
                .active(true)
                .build();
    }

    // ── createProject ──────────────────────────────────────────────────────

    @Test
    void createProject_guardaYPublicaEvento() {
        ProjectRequest req = projectRequest();
        when(projectRepository.save(any())).thenReturn(draftProject);

        ProjectResponse response = projectService.createProject(req, 10L);

        assertThat(response.getName()).isEqualTo("Solar Test");
        verify(projectRepository).save(any(Project.class));
        verify(eventPublisher).publishProjectCreated(any(Project.class));
    }

    @Test
    void createProject_asignaOwnerIdDelToken() {
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        projectService.createProject(projectRequest(), 42L);

        verify(projectRepository).save(argThat(p -> p.getOwnerId().equals(42L)));
    }

    @Test
    void createProject_sinDescripcion_lanzaIllegalArgument() {
        ProjectRequest req = projectRequest();
        req.setDescription(null);

        assertThatThrownBy(() -> projectService.createProject(req, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("descripción");
    }

    @Test
    void createProject_descripcionBlank_lanzaIllegalArgument() {
        ProjectRequest req = projectRequest();
        req.setDescription("   ");

        assertThatThrownBy(() -> projectService.createProject(req, 10L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── getProject ─────────────────────────────────────────────────────────

    @Test
    void getProject_existente_devuelveResponse() {
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));

        ProjectResponse response = projectService.getProject(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Solar Test");
    }

    @Test
    void getProject_noExiste_lanzaProjectNotFoundException() {
        when(projectRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProject(999L))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    // ── changeState ────────────────────────────────────────────────────────

    @Test
    void changeState_transicionValida_actualizaEstadoYPublicaEvento() {
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse response = projectService.changeState(1L, ProjectState.PRE_OPEN, 10L, false);

        assertThat(response.getState()).isEqualTo(ProjectState.PRE_OPEN);
        verify(eventPublisher).publishStateChanged(any(), eq(ProjectState.DRAFT), eq(ProjectState.PRE_OPEN));
    }

    @Test
    void changeState_transicionInvalida_lanzaProjectStateException() {
        draftProject.setState(ProjectState.CLOSED);
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));

        assertThatThrownBy(() -> projectService.changeState(1L, ProjectState.OPEN, 10L, false))
                .isInstanceOf(ProjectStateException.class);
    }

    // ── cancelación: reglas por rol ────────────────────────────────────────
    // Roles: admin | dev (owner) | investor
    // DRAFT / PRE_OPEN → dev owner puede cancelar (sin inversores)
    // OPEN             → solo admin puede cancelar (hay inversores con tokens)
    // CLOSED           → nadie puede cancelar (estado final)

    @Test
    void changeState_devCancelaDesdePreOpen_permitido() {
        draftProject.setState(ProjectState.PRE_OPEN);
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(
                () -> projectService.changeState(1L, ProjectState.CANCELLED, 10L, false)
        );
    }

    @Test
    void changeState_devCancelaDesdeOpen_noPermitido() {
        draftProject.setState(ProjectState.OPEN);
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));

        assertThatThrownBy(() -> projectService.changeState(1L, ProjectState.CANCELLED, 10L, false))
                .isInstanceOf(UnauthorizedProjectAccessException.class)
                .hasMessageContaining("administrador");
    }

    @Test
    void changeState_adminCancelaDesdeOpen_permitido() {
        draftProject.setState(ProjectState.OPEN);
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse response = projectService.changeState(1L, ProjectState.CANCELLED, 99L, true);

        assertThat(response.getState()).isEqualTo(ProjectState.CANCELLED);
        verify(eventPublisher).publishStateChanged(any(), eq(ProjectState.OPEN), eq(ProjectState.CANCELLED));
    }

    @Test
    void changeState_cancelacionDesdeClosed_noPosible() {
        draftProject.setState(ProjectState.CLOSED);
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));

        assertThatThrownBy(() -> projectService.changeState(1L, ProjectState.CANCELLED, 10L, true))
                .isInstanceOf(ProjectStateException.class);
    }

    @Test
    void changeState_noEsOwnerNiAdmin_lanzaUnauthorized() {
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));

        assertThatThrownBy(() -> projectService.changeState(1L, ProjectState.PRE_OPEN, 99L, false))
                .isInstanceOf(UnauthorizedProjectAccessException.class);
    }

    @Test
    void changeState_adminPuedeModificarProyectoAjeno() {
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(
                () -> projectService.changeState(1L, ProjectState.PRE_OPEN, 99L, true)
        );
    }

    // ── deleteProject ──────────────────────────────────────────────────────

    @Test
    void deleteProject_estadoDraft_softDeleteExitoso() {
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        projectService.deleteProject(1L, 10L, false);

        assertThat(draftProject.getActive()).isFalse();
        verify(projectRepository).save(draftProject);
    }

    @Test
    void deleteProject_estadoOpen_tambiénPermitido() {
        // RF002.001.003: la baja aplica en cualquier estado
        draftProject.setState(ProjectState.OPEN);
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(() -> projectService.deleteProject(1L, 10L, false));
        assertThat(draftProject.getActive()).isFalse();
    }

    @Test
    void deleteProject_noEsOwner_lanzaUnauthorized() {
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));

        assertThatThrownBy(() -> projectService.deleteProject(1L, 99L, false))
                .isInstanceOf(UnauthorizedProjectAccessException.class);
    }

    // ── updateProject ──────────────────────────────────────────────────────

    @Test
    void updateProject_ownerPuedeActualizar() {
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProjectRequest req = projectRequest();
        req.setName("Nombre actualizado");

        ProjectResponse response = projectService.updateProject(1L, req, 10L, false);

        assertThat(response.getName()).isEqualTo("Nombre actualizado");
    }

    @Test
    void updateProject_noEsOwnerNiAdmin_lanzaUnauthorized() {
        when(projectRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(draftProject));

        assertThatThrownBy(() -> projectService.updateProject(1L, projectRequest(), 99L, false))
                .isInstanceOf(UnauthorizedProjectAccessException.class);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private ProjectRequest projectRequest() {
        ProjectRequest req = new ProjectRequest();
        req.setName("Solar Test");
        req.setDescription("Parque solar de prueba en Mendoza.");
        req.setEnergyType(EnergyType.SOLAR);
        req.setTotalTokens(new BigDecimal("10000"));
        req.setTokenPrice(new BigDecimal("10.00"));
        return req;
    }
}

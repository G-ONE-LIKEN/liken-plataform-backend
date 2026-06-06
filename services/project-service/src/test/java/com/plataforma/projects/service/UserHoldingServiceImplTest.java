package com.plataforma.projects.service;

import com.plataforma.projects.dto.UserHoldingResponse;
import com.plataforma.projects.exception.ProjectNotFoundException;
import com.plataforma.projects.model.EnergyType;
import com.plataforma.projects.model.Project;
import com.plataforma.projects.model.ProjectState;
import com.plataforma.projects.model.UserHolding;
import com.plataforma.projects.repository.ProcessedEventRepository;
import com.plataforma.projects.repository.ProjectRepository;
import com.plataforma.projects.repository.UserHoldingRepository;
import com.plataforma.projects.service.impl.UserHoldingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UserHoldingServiceImplTest {

    @Mock UserHoldingRepository holdingRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProcessedEventRepository processedEventRepository;
    @InjectMocks UserHoldingServiceImpl userHoldingService;

    private Project project;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(1L)
                .name("Test Project")
                .ownerId(10L)
                .state(ProjectState.OPEN)
                .energyType(EnergyType.SOLAR)
                .totalTokens(new BigDecimal("10000"))
                .earlyBirdPrice(new BigDecimal("8.0000"))
                .standardPrice(new BigDecimal("10.0000"))
                .active(true)
                .build();
    }

    // ── updateHolding ──────────────────────────────────────────────────────

    @Test
    void updateHolding_holdingNuevo_creaConMontoCorreto() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(holdingRepository.findByUserIdAndProjectId(5L, 1L)).thenReturn(Optional.empty());
        when(holdingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userHoldingService.updateHolding(5L, 1L, new BigDecimal("100"), null);

        ArgumentCaptor<UserHolding> captor = ArgumentCaptor.forClass(UserHolding.class);
        verify(holdingRepository).save(captor.capture());
        assertThat(captor.getValue().getTokensAmount()).isEqualByComparingTo("100");
        assertThat(captor.getValue().getUserId()).isEqualTo(5L);
    }

    @Test
    void updateHolding_holdingExistente_acumulaDelta() {
        UserHolding existing = UserHolding.builder()
                .userId(5L).project(project).tokensAmount(new BigDecimal("200")).build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(holdingRepository.findByUserIdAndProjectId(5L, 1L)).thenReturn(Optional.of(existing));
        when(holdingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userHoldingService.updateHolding(5L, 1L, new BigDecimal("50"), null);

        assertThat(existing.getTokensAmount()).isEqualByComparingTo("250");
    }

    @Test
    void updateHolding_deltaNegativos_reduceSaldo() {
        UserHolding existing = UserHolding.builder()
                .userId(5L).project(project).tokensAmount(new BigDecimal("200")).build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(holdingRepository.findByUserIdAndProjectId(5L, 1L)).thenReturn(Optional.of(existing));
        when(holdingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userHoldingService.updateHolding(5L, 1L, new BigDecimal("-80"), null);

        assertThat(existing.getTokensAmount()).isEqualByComparingTo("120");
    }

    @Test
    void updateHolding_resultadoNegativo_noGuarda() {
        UserHolding existing = UserHolding.builder()
                .userId(5L).project(project).tokensAmount(new BigDecimal("30")).build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(holdingRepository.findByUserIdAndProjectId(5L, 1L)).thenReturn(Optional.of(existing));

        userHoldingService.updateHolding(5L, 1L, new BigDecimal("-100"), null);

        verify(holdingRepository, never()).save(any());
        // el monto original no se modifica
        assertThat(existing.getTokensAmount()).isEqualByComparingTo("30");
    }

    @Test
    void updateHolding_proyectoNoExiste_lanzaProjectNotFoundException() {
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userHoldingService.updateHolding(5L, 999L, BigDecimal.TEN, null))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    // ── listHolders ────────────────────────────────────────────────────────

    @Test
    void listHolders_proyectoExistente_devuelvePagina() {
        UserHolding holding = UserHolding.builder()
                .userId(5L).project(project).tokensAmount(new BigDecimal("100")).build();

        when(projectRepository.existsById(1L)).thenReturn(true);
        when(holdingRepository.findByProjectId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(holding)));

        var page = userHoldingService.listHolders(1L, Pageable.unpaged());

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getUserId()).isEqualTo(5L);
        assertThat(page.getContent().get(0).getTokensAmount()).isEqualByComparingTo("100");
    }

    @Test
    void listHolders_proyectoNoExiste_lanzaProjectNotFoundException() {
        when(projectRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> userHoldingService.listHolders(999L, Pageable.unpaged()))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}

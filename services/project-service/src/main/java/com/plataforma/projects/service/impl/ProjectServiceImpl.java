package com.plataforma.projects.service.impl;

import com.plataforma.projects.dto.ActiveProjectOracleDto;
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
import com.plataforma.projects.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectEventPublisher eventPublisher;

    @Override
    public Page<ProjectResponse> listProjects(ProjectState state, EnergyType energyType, Pageable pageable) {
        Page<Project> page;
        if (state != null && energyType != null) {
            page = projectRepository.findByActiveTrueAndStateAndEnergyType(state, energyType, pageable);
        } else if (state != null) {
            page = projectRepository.findByActiveTrueAndState(state, pageable);
        } else if (energyType != null) {
            // Excluye proyectos pendientes de aprobación del listado público
            page = projectRepository.findByActiveTrueAndStateNotAndEnergyType(ProjectState.PENDING_APPROVAL, energyType,
                    pageable);
        } else {
            page = projectRepository.findByActiveTrueAndStateNot(ProjectState.PENDING_APPROVAL, pageable);
        }
        return page.map(ProjectResponse::from);
    }

    @Override
    public Page<ProjectResponse> listPendingApproval(Pageable pageable) {
        return projectRepository.findByActiveTrueAndState(ProjectState.PENDING_APPROVAL, pageable)
                .map(ProjectResponse::from);
    }

    @Override
    public Page<ProjectResponse> listMyProjects(Long ownerId, Pageable pageable) {
        return projectRepository.findByActiveTrueAndOwnerId(ownerId, pageable)
                .map(ProjectResponse::from);
    }

    @Override
    public ProjectResponse getProject(Long id) {
        return ProjectResponse.from(findActiveOrThrow(id));
    }

    @Override
    @Transactional
    public ProjectResponse createProject(ProjectRequest request, Long ownerId, boolean isAdmin) {
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new IllegalArgumentException("La descripción del proyecto es obligatoria");
        }

        if (request.getSoftCap() != null && request.getHardCap() != null
                && request.getSoftCap().compareTo(request.getHardCap()) >= 0) {
            throw new IllegalArgumentException("El soft cap debe ser menor que el hard cap");
        }

        // Si lo crea un admin, va directo a DRAFT y se autoaprueba.
        // Si lo crea un developer, queda pendiente de aprobación.
        ProjectState initialState = isAdmin ? ProjectState.DRAFT : ProjectState.PENDING_APPROVAL;

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(ownerId)
                .state(initialState)
                .energyType(request.getEnergyType())
                .province(request.getProvince())
                .country(request.getCountry() != null ? request.getCountry() : "Argentina")
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .installedCapacityMW(request.getInstalledCapacityMW())
                .totalTokens(request.getTotalTokens())
                .tokenPrice(request.getTokenPrice())
                .minimumInvestment(request.getMinimumInvestment())
                .softCap(request.getSoftCap())
                .hardCap(request.getHardCap())
                .softCapDeadline(request.getSoftCapDeadline())
                .expectedOpenDate(request.getExpectedOpenDate())
                .expectedAnnualYield(request.getExpectedAnnualYield())
                .expectedAnnualProductionMWh(request.getExpectedAnnualProductionMWh())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        if (isAdmin) {
            project.setApprovedBy(ownerId);
            project.setApprovedAt(LocalDateTime.now());
        }

        Project saved = projectRepository.save(project);
        if (isAdmin) {
            eventPublisher.publishProjectCreated(saved);
        } else {
            eventPublisher.publishPendingApproval(saved);
        }
        return ProjectResponse.from(saved);
    }

    @Override
    @Transactional
    public ProjectResponse approveProject(Long id, Long adminId) {
        Project project = findActiveOrThrow(id);
        if (project.getState() != ProjectState.PENDING_APPROVAL) {
            throw new ProjectStateException("El proyecto no está pendiente de aprobación");
        }
        ProjectState oldState = project.getState();
        project.setState(ProjectState.DRAFT);
        project.setApprovedBy(adminId);
        project.setApprovedAt(LocalDateTime.now());
        project.setRejectionReason(null);
        Project saved = projectRepository.save(project);
        eventPublisher.publishProjectApproved(saved, adminId);
        eventPublisher.publishStateChanged(saved, oldState, ProjectState.DRAFT);
        return ProjectResponse.from(saved);
    }

    @Override
    @Transactional
    public ProjectResponse rejectProject(Long id, Long adminId, String reason) {
        Project project = findActiveOrThrow(id);
        if (project.getState() != ProjectState.PENDING_APPROVAL) {
            throw new ProjectStateException("El proyecto no está pendiente de aprobación");
        }
        ProjectState oldState = project.getState();
        project.setState(ProjectState.CANCELLED);
        project.setRejectionReason(reason);
        project.setApprovedBy(adminId);
        project.setApprovedAt(LocalDateTime.now());
        Project saved = projectRepository.save(project);
        eventPublisher.publishProjectRejected(saved, adminId, reason);
        eventPublisher.publishStateChanged(saved, oldState, ProjectState.CANCELLED);
        return ProjectResponse.from(saved);
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(Long id, ProjectRequest request, Long requesterId, boolean isAdmin) {
        Project project = findActiveOrThrow(id);
        checkOwnership(project, requesterId, isAdmin);

        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setEnergyType(request.getEnergyType());
        project.setProvince(request.getProvince());
        if (request.getCountry() != null)
            project.setCountry(request.getCountry());
        project.setLatitude(request.getLatitude());
        project.setLongitude(request.getLongitude());
        project.setInstalledCapacityMW(request.getInstalledCapacityMW());
        project.setTotalTokens(request.getTotalTokens());
        project.setTokenPrice(request.getTokenPrice());
        project.setMinimumInvestment(request.getMinimumInvestment());
        project.setSoftCap(request.getSoftCap());
        project.setHardCap(request.getHardCap());
        project.setSoftCapDeadline(request.getSoftCapDeadline());
        project.setExpectedOpenDate(request.getExpectedOpenDate());
        project.setExpectedAnnualYield(request.getExpectedAnnualYield());
        project.setExpectedAnnualProductionMWh(request.getExpectedAnnualProductionMWh());
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());

        return ProjectResponse.from(projectRepository.save(project));
    }

    @Override
    @Transactional
    public void deleteProject(Long id, Long requesterId, boolean isAdmin) {
        Project project = findActiveOrThrow(id);
        checkOwnership(project, requesterId, isAdmin);

        // RF002.001.003: la baja es válida en cualquier estado.
        // Penalizaciones económicas / reembolsos quedan a definir según proyecto.
        project.setActive(false);
        projectRepository.save(project);
    }

    @Override
    @Transactional
    public ProjectResponse changeState(Long id, ProjectState newState, Long requesterId, boolean isAdmin) {
        Project project = findActiveOrThrow(id);
        checkOwnership(project, requesterId, isAdmin);

        // Roles: admin | dev (owner) | investor
        // Cancelación desde OPEN: solo admin puede hacerlo (ya hay inversores con
        // tokens).
        // Cancelación desde DRAFT / PRE_OPEN: el dev owner también puede.
        if (newState == ProjectState.CANCELLED
                && project.getState() == ProjectState.OPEN
                && !isAdmin) {
            throw new UnauthorizedProjectAccessException(
                    "Solo un administrador puede cancelar un proyecto que ya está abierto a inversiones");
        }

        ProjectState oldState = project.getState();
        project.advanceState(newState);
        Project saved = projectRepository.save(project);
        eventPublisher.publishStateChanged(saved, oldState, newState);
        return ProjectResponse.from(saved);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Project findActiveOrThrow(Long id) {
        return projectRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    private void checkOwnership(Project project, Long requesterId, boolean isAdmin) {
        if (!isAdmin && !project.getOwnerId().equals(requesterId)) {
            throw new UnauthorizedProjectAccessException();
        }
    }

    @Override
    public List<ActiveProjectOracleDto> listActiveProjectsForOracle() {
        return projectRepository
                .findByActiveTrueAndStateAndInstalledCapacityMWIsNotNull(ProjectState.OPEN)
                .stream()
                .map(ActiveProjectOracleDto::from)
                .toList();
    }
}

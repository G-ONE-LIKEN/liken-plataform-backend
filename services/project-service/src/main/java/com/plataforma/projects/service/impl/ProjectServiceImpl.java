package com.plataforma.projects.service.impl;

import com.plataforma.projects.dto.ProjectRequest;
import com.plataforma.projects.dto.ProjectResponse;
import com.plataforma.projects.dto.internal.ActiveProjectOracleDto;
import com.plataforma.projects.dto.internal.OfferingContractRefResponse;
import com.plataforma.projects.dto.internal.ProjectPublicationFailureRequest;
import com.plataforma.projects.dto.internal.ProjectPublicationRequest;
import com.plataforma.projects.dto.internal.ProjectPublicationSuccessRequest;
import com.plataforma.projects.event.ProjectEventPublisher;
import com.plataforma.projects.exception.ProjectNotFoundException;
import com.plataforma.projects.exception.ProjectStateException;
import com.plataforma.projects.exception.UnauthorizedProjectAccessException;
import com.plataforma.projects.model.EnergyType;
import com.plataforma.projects.model.OnChainStatus;
import com.plataforma.projects.model.Project;
import com.plataforma.projects.model.ProjectState;
import com.plataforma.projects.model.RoundState;
import com.plataforma.projects.repository.ProjectRepository;
import com.plataforma.projects.service.BlockchainPublicationClient;
import com.plataforma.projects.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectEventPublisher eventPublisher;
    private final BlockchainPublicationClient blockchainPublicationClient;

    @Override
    public Page<ProjectResponse> listProjects(ProjectState state, EnergyType energyType, Pageable pageable) {
        Page<Project> page;
        if (state != null && energyType != null) {
            page = projectRepository.findByActiveTrueAndStateAndEnergyType(state, energyType, pageable);
        } else if (state != null) {
            page = projectRepository.findByActiveTrueAndState(state, pageable);
        } else if (energyType != null) {
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
            throw new IllegalArgumentException("La descripcion del proyecto es obligatoria");
        }

        if (request.getSoftCap() != null && request.getHardCap() != null
                && request.getSoftCap().compareTo(request.getHardCap()) >= 0) {
            throw new IllegalArgumentException("El soft cap debe ser menor que el hard cap");
        }

        if (request.getEarlyBirdPrice() != null && request.getStandardPrice() != null
                && request.getEarlyBirdPrice().compareTo(request.getStandardPrice()) >= 0) {
            throw new IllegalArgumentException(
                    "El early bird price debe ser estrictamente menor que el standard price");
        }

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
                .earlyBirdPrice(request.getEarlyBirdPrice())
                .standardPrice(request.getStandardPrice())
                .minimumInvestment(request.getMinimumInvestment())
                .softCap(request.getSoftCap())
                .hardCap(request.getHardCap())
                .expectedOpenDate(request.getExpectedOpenDate())
                .expectedAnnualYield(request.getExpectedAnnualYield())
                .expectedAnnualProductionMWh(request.getExpectedAnnualProductionMWh())
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
            throw new ProjectStateException("El proyecto no esta pendiente de aprobacion");
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
            throw new ProjectStateException("El proyecto no esta pendiente de aprobacion");
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
        if (request.getEarlyBirdPrice() != null && request.getStandardPrice() != null
                && request.getEarlyBirdPrice().compareTo(request.getStandardPrice()) >= 0) {
            throw new IllegalArgumentException(
                    "El early bird price debe ser estrictamente menor que el standard price");
        }
        if (request.getEarlyBirdPrice() != null)
            project.setEarlyBirdPrice(request.getEarlyBirdPrice());
        if (request.getStandardPrice() != null)
            project.setStandardPrice(request.getStandardPrice());
        project.setMinimumInvestment(request.getMinimumInvestment());
        project.setSoftCap(request.getSoftCap());
        project.setHardCap(request.getHardCap());
        project.setExpectedOpenDate(request.getExpectedOpenDate());
        project.setExpectedAnnualYield(request.getExpectedAnnualYield());
        project.setExpectedAnnualProductionMWh(request.getExpectedAnnualProductionMWh());

        return ProjectResponse.from(projectRepository.save(project));
    }

    @Override
    @Transactional
    public void deleteProject(Long id, Long requesterId, boolean isAdmin) {
        Project project = findActiveOrThrow(id);
        checkOwnership(project, requesterId, isAdmin);
        project.setActive(false);
        projectRepository.save(project);
    }

    @Override
    @Transactional
    public ProjectResponse changeState(Long id, ProjectState newState, Long requesterId, boolean isAdmin) {
        Project project = findActiveOrThrow(id);
        checkOwnership(project, requesterId, isAdmin);

        if (project.getState() == ProjectState.PRE_OPEN
                && (newState == ProjectState.OPEN || newState == ProjectState.CANCELLED)) {
            throw new ProjectStateException(
                    "La transicion " + project.getState() + " -> " + newState
                            + " la dispara el OfferingContract on-chain, no este endpoint.");
        }

        if (newState == ProjectState.CANCELLED
                && project.getState() == ProjectState.OPEN
                && !isAdmin) {
            throw new UnauthorizedProjectAccessException(
                    "Solo un administrador puede cancelar un proyecto que ya esta abierto a inversiones");
        }

        if (project.getState() == ProjectState.DRAFT && newState == ProjectState.PRE_OPEN) {
            return startPublication(project);
        }

        ProjectState oldState = project.getState();
        project.advanceState(newState);
        Project saved = projectRepository.save(project);
        eventPublisher.publishStateChanged(saved, oldState, newState);
        return ProjectResponse.from(saved);
    }

    @Override
    @Transactional
    public void markPublicationSucceeded(ProjectPublicationSuccessRequest request) {
        Project project = findActiveOrThrow(request.getProjectId());
        ProjectState oldState = project.getState();

        project.setRegistryProjectId(request.getRegistryProjectId());
        project.setOfferingContractAddress(request.getOfferingContractAddress());
        project.setDeployTxHash(request.getDeployTxHash());
        project.setDeployBlockNumber(request.getDeployBlockNumber());
        project.setOnChainStatus(OnChainStatus.DEPLOYED);
        project.setRoundState(RoundState.OPEN);
        if (project.getState() == ProjectState.DRAFT) {
            project.setState(ProjectState.PRE_OPEN);
        }

        Project saved = projectRepository.save(project);
        if (oldState != saved.getState()) {
            eventPublisher.publishStateChanged(saved, oldState, saved.getState());
        }
        log.info("Publicacion on-chain confirmada: projectId={} registryProjectId={} offering={}",
                saved.getId(), saved.getRegistryProjectId(), saved.getOfferingContractAddress());
    }

    @Override
    @Transactional
    public void markPublicationFailed(ProjectPublicationFailureRequest request) {
        Project project = findActiveOrThrow(request.getProjectId());
        project.setOnChainStatus(OnChainStatus.FAILED);
        project.setRoundState(null);
        if (request.getDeployTxHash() != null && !request.getDeployTxHash().isBlank()) {
            project.setDeployTxHash(request.getDeployTxHash());
        }
        projectRepository.save(project);
        log.warn("Publicacion on-chain fallida: projectId={} error={}",
                project.getId(), request.getErrorMessage());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfferingContractRefResponse> listOfferingContracts() {
        return projectRepository.findByActiveTrueAndOfferingContractAddressIsNotNull().stream()
                .map(project -> OfferingContractRefResponse.builder()
                        .projectId(project.getId())
                        .registryProjectId(project.getRegistryProjectId())
                        .offeringContractAddress(project.getOfferingContractAddress())
                        .build())
                .toList();
    }

    private ProjectResponse startPublication(Project project) {
        if (project.getOnChainStatus() == OnChainStatus.DEPLOYING) {
            throw new ProjectStateException(
                    "El proyecto ya se esta publicando on-chain. Espera a que termine el deploy.");
        }

        if (project.getExpectedOpenDate() == null) {
            throw new ProjectStateException(
                    "No se puede publicar sin expectedOpenDate. Esa fecha define el deadline on-chain.");
        }

        if (project.getSoftCap() == null || project.getHardCap() == null) {
            throw new ProjectStateException("No se puede publicar sin softCap y hardCap configurados.");
        }

        project.setOnChainStatus(OnChainStatus.DEPLOYING);
        project.setRoundState(RoundState.PENDING);
        Project saved = projectRepository.save(project);

        try {
            blockchainPublicationClient.requestPublication(ProjectPublicationRequest.builder()
                    .projectId(saved.getId())
                    .ownerId(saved.getOwnerId())
                    .projectName(saved.getName())
                    .projectDescription(saved.getDescription())
                    .earlyBirdPrice(saved.getEarlyBirdPrice())
                    .standardPrice(saved.getStandardPrice())
                    .totalTokens(saved.getTotalTokens())
                    .softCap(saved.getSoftCap())
                    .hardCap(saved.getHardCap())
                    .expectedOpenDate(saved.getExpectedOpenDate())
                    .build());
        } catch (RuntimeException ex) {
            saved.setOnChainStatus(OnChainStatus.FAILED);
            saved.setRoundState(null);
            projectRepository.save(saved);
            throw new ProjectStateException("No pude iniciar la publicacion on-chain del proyecto: " + ex.getMessage());
        }

        return ProjectResponse.from(saved);
    }

    private Project findActiveOrThrow(Long id) {
        return projectRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    private void checkOwnership(Project project, Long requesterId, boolean isAdmin) {
        if (!isAdmin && !project.getOwnerId().equals(requesterId)) {
            throw new UnauthorizedProjectAccessException();
        }
    }

    public List<ActiveProjectOracleDto> listActiveProjectsForOracle() {
        return projectRepository
                .findByActiveTrueAndStateAndInstalledCapacityMWIsNotNull(ProjectState.OPEN)
                .stream()
                .map(p -> new ActiveProjectOracleDto(p.getId(), p.getInstalledCapacityMW()))
                .toList();
    }
}

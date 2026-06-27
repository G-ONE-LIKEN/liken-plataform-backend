package com.plataforma.projects.service;

import com.plataforma.projects.dto.ProjectRequest;
import com.plataforma.projects.dto.ProjectResponse;
import com.plataforma.projects.dto.internal.ActiveProjectOracleDto;
import com.plataforma.projects.dto.internal.HolderDto;
import com.plataforma.projects.dto.internal.OfferingContractRefResponse;
import com.plataforma.projects.dto.internal.ProjectPublicationFailureRequest;
import com.plataforma.projects.dto.internal.ProjectPublicationSuccessRequest;
import com.plataforma.projects.model.EnergyType;
import com.plataforma.projects.model.ProjectState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface ProjectService {
    Page<ProjectResponse> listProjects(ProjectState state, EnergyType energyType, Pageable pageable);

    Page<ProjectResponse> listPendingApproval(Pageable pageable);

    Page<ProjectResponse> listMyProjects(Long ownerId, Pageable pageable);

    ProjectResponse getProject(Long id);

    ProjectResponse createProject(ProjectRequest request, Long ownerId, boolean isAdmin);

    ProjectResponse updateProject(Long id, ProjectRequest request, Long requesterId, boolean isAdmin);

    void deleteProject(Long id, Long requesterId, boolean isAdmin);

    ProjectResponse changeState(Long id, ProjectState newState, Long requesterId, boolean isAdmin);

    ProjectResponse approveProject(Long id, Long adminId);

    ProjectResponse rejectProject(Long id, Long adminId, String reason);

    void markPublicationSucceeded(ProjectPublicationSuccessRequest request);

    void markPublicationFailed(ProjectPublicationFailureRequest request);

    List<OfferingContractRefResponse> listOfferingContracts();

    public List<ActiveProjectOracleDto> listActiveProjectsForOracle();

    /**
     * Holders activos del proyecto (con balance > 0 y wallet vinculada).
     * Usado por invest-dividend-service para repartir dividendos.
     */
    List<HolderDto> listHoldersByProject(Long projectId);

    /**
     * Acumula una compra primaria en el proyecto y, si el {@code raisedAmount}
     * cruza el {@code softCap} estando en PRE_OPEN, transiciona el estado a OPEN
     * para reflejar que la ronda ya es exitosa (parque tradeable en marketplace).
     * La ronda primaria on-chain sigue activa hasta hardCap o deadline.
     */
    void applyPurchaseAccrual(Long projectId, BigDecimal usdcAmount, BigDecimal lknAmount);
}

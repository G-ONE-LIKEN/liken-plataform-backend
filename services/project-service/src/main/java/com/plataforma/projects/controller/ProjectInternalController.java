package com.plataforma.projects.controller;

import com.plataforma.projects.dto.internal.ActiveProjectOracleDto;
import com.plataforma.projects.dto.internal.OfferingContractRefResponse;
import com.plataforma.projects.dto.internal.ProjectPublicationFailureRequest;
import com.plataforma.projects.dto.internal.ProjectPublicationSuccessRequest;
import com.plataforma.projects.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/projects")
@RequiredArgsConstructor
public class ProjectInternalController {

    private final ProjectService projectService;
    private final com.plataforma.projects.service.UserHoldingService userHoldingService;

    @GetMapping("/{id}")
    public ResponseEntity<com.plataforma.projects.dto.ProjectResponse> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProject(id));
    }

    @GetMapping("/{projectId}/holders/{userId}")
    public ResponseEntity<com.plataforma.projects.dto.UserHoldingResponse> getUserHolding(
            @PathVariable Long projectId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(userHoldingService.getUserHolding(projectId, userId));
    }

    @GetMapping("/offering-contracts")
    public ResponseEntity<List<OfferingContractRefResponse>> listOfferingContracts() {
        return ResponseEntity.ok(projectService.listOfferingContracts());
    }

    @PostMapping("/publication-success")
    public ResponseEntity<Void> markPublicationSucceeded(
            @RequestBody ProjectPublicationSuccessRequest request) {
        projectService.markPublicationSucceeded(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/publication-failure")
    public ResponseEntity<Void> markPublicationFailed(
            @RequestBody ProjectPublicationFailureRequest request) {
        projectService.markPublicationFailed(request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/active")
    public ResponseEntity<List<ActiveProjectOracleDto>> listActiveProjectsForOracle() {
        return ResponseEntity.ok(projectService.listActiveProjectsForOracle());
    }

    /**
     * Holders del proyecto para repartir dividendos. Consumido por
     * invest-dividend-service.
     */
    @GetMapping("/{id}/holders")
    public ResponseEntity<List<com.plataforma.projects.dto.internal.HolderDto>> holders(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.listHoldersByProject(id));
    }
}

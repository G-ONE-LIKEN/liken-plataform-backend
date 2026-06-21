package com.plataforma.projects.controller;

import com.plataforma.projects.dto.*;
import com.plataforma.projects.model.EnergyType;
import com.plataforma.projects.model.ProjectState;
import com.plataforma.projects.service.ProjectService;
import com.plataforma.projects.service.UserHoldingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final UserHoldingService userHoldingService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> listProjects(
            @RequestParam(required = false) ProjectState state,
            @RequestParam(required = false) EnergyType energyType,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<ProjectResponse> projects = projectService.listProjects(state, energyType, pageable);
        return ResponseEntity.ok(ApiResponse.success("Proyectos obtenidos exitosamente", projects));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Proyecto obtenido exitosamente", projectService.getProject(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('developer:approved')")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody ProjectRequest request,
            Authentication auth) {

        Long ownerId = (Long) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        ProjectResponse created = projectService.createProject(request, ownerId, isAdmin);
        String message = isAdmin
                ? "Proyecto creado exitosamente"
                : "Proyecto creado, a la espera de aprobacion de un administrador";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(message, created));
    }

    @GetMapping("/pending-approval")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> listPendingApproval(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Proyectos pendientes obtenidos",
                projectService.listPendingApproval(pageable)));
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> listMyProjects(
            Authentication auth,
            @PageableDefault(size = 20) Pageable pageable) {
        Long ownerId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Mis proyectos obtenidos",
                projectService.listMyProjects(ownerId, pageable)));
    }

    @GetMapping("/holdings/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<java.util.List<UserHoldingResponse>>> getMyHoldings(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Mis holdings obtenidos", userHoldingService.listMyHoldings(userId)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProjectResponse>> approveProject(
            @PathVariable Long id,
            Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Proyecto aprobado",
                projectService.approveProject(id, adminId)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProjectResponse>> rejectProject(
            @PathVariable Long id,
            @RequestBody(required = false) RejectProjectRequest request,
            Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(ApiResponse.success("Proyecto rechazado",
                projectService.rejectProject(id, adminId, reason)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('developer:approved')")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequest request,
            Authentication auth) {

        Long requesterId = (Long) auth.getPrincipal();
        boolean isAdmin  = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(ApiResponse.success("Proyecto actualizado exitosamente",
                projectService.updateProject(id, request, requesterId, isAdmin)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('developer:approved')")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @PathVariable Long id,
            Authentication auth) {

        Long requesterId = (Long) auth.getPrincipal();
        boolean isAdmin  = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        projectService.deleteProject(id, requesterId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Proyecto eliminado exitosamente", null));
    }

    @PutMapping("/{id}/state")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('developer:approved')")
    public ResponseEntity<ApiResponse<ProjectResponse>> changeState(
            @PathVariable Long id,
            @RequestBody StateChangeRequest request,
            Authentication auth) {

        Long requesterId = (Long) auth.getPrincipal();
        boolean isAdmin  = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(ApiResponse.success("Estado del proyecto actualizado",
                projectService.changeState(id, request.getState(), requesterId, isAdmin)));
    }

    @GetMapping("/{id}/holders")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('developer:approved')")
    public ResponseEntity<ApiResponse<Page<UserHoldingResponse>>> listHolders(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success("Holders obtenidos exitosamente",
                userHoldingService.listHolders(id, pageable)));
    }
}

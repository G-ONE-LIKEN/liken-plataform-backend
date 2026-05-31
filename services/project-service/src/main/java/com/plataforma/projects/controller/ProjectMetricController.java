package com.plataforma.projects.controller;

import com.plataforma.projects.dto.ApiResponse;
import com.plataforma.projects.dto.ProjectMetricRequest;
import com.plataforma.projects.dto.ProjectMetricResponse;
import com.plataforma.projects.service.ProjectMetricService;
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
@RequestMapping("/api/projects/{projectId}/metrics")
@RequiredArgsConstructor
public class ProjectMetricController {

    private final ProjectMetricService metricService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProjectMetricResponse>>> listMetrics(
            @PathVariable Long projectId,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success("Métricas obtenidas exitosamente",
                metricService.listMetrics(projectId, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('developer:approved')")
    public ResponseEntity<ApiResponse<ProjectMetricResponse>> addMetric(
            @PathVariable Long projectId,
            @RequestBody ProjectMetricRequest request,
            Authentication auth) {

        Long requesterId = (Long) auth.getPrincipal();
        boolean isAdmin  = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        ProjectMetricResponse metric = metricService.addMetric(projectId, request, requesterId, isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Métrica registrada exitosamente", metric));
    }
}

package com.plataforma.projects.service;

import com.plataforma.projects.dto.ProjectMetricRequest;
import com.plataforma.projects.dto.ProjectMetricResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectMetricService {
    Page<ProjectMetricResponse> listMetrics(Long projectId, Pageable pageable);
    ProjectMetricResponse addMetric(Long projectId, ProjectMetricRequest request, Long requesterId, boolean isAdmin);
}

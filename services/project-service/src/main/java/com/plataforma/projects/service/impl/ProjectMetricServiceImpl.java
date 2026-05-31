package com.plataforma.projects.service.impl;

import com.plataforma.projects.dto.ProjectMetricRequest;
import com.plataforma.projects.dto.ProjectMetricResponse;
import com.plataforma.projects.event.ProjectEventPublisher;
import com.plataforma.projects.exception.ProjectNotFoundException;
import com.plataforma.projects.exception.UnauthorizedProjectAccessException;
import com.plataforma.projects.model.Project;
import com.plataforma.projects.model.ProjectMetric;
import com.plataforma.projects.repository.ProjectMetricRepository;
import com.plataforma.projects.repository.ProjectRepository;
import com.plataforma.projects.service.ProjectMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectMetricServiceImpl implements ProjectMetricService {

    private final ProjectMetricRepository metricRepository;
    private final ProjectRepository projectRepository;
    private final ProjectEventPublisher eventPublisher;

    @Override
    public Page<ProjectMetricResponse> listMetrics(Long projectId, Pageable pageable) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
        return metricRepository.findByProjectId(projectId, pageable).map(ProjectMetricResponse::from);
    }

    @Override
    @Transactional
    public ProjectMetricResponse addMetric(Long projectId, ProjectMetricRequest request, Long requesterId, boolean isAdmin) {
        Project project = projectRepository.findByIdAndActiveTrue(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (!isAdmin && !project.getOwnerId().equals(requesterId)) {
            throw new UnauthorizedProjectAccessException();
        }

        ProjectMetric metric = ProjectMetric.builder()
                .project(project)
                .periodStart(request.getPeriodStart())
                .periodEnd(request.getPeriodEnd())
                .energyGeneratedKwh(request.getEnergyGeneratedKwh())
                .revenueGenerated(request.getRevenueGenerated())
                .source(request.getSource())
                .build();

        ProjectMetric saved = metricRepository.save(metric);
        eventPublisher.publishMetricsUpdated(saved);
        return ProjectMetricResponse.from(saved);
    }
}

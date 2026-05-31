package com.plataforma.projects.service;

import com.plataforma.projects.dto.ProjectRequest;
import com.plataforma.projects.dto.ProjectResponse;
import com.plataforma.projects.model.EnergyType;
import com.plataforma.projects.model.ProjectState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectService {
    Page<ProjectResponse> listProjects(ProjectState state, EnergyType energyType, Pageable pageable);
    ProjectResponse getProject(Long id);
    ProjectResponse createProject(ProjectRequest request, Long ownerId);
    ProjectResponse updateProject(Long id, ProjectRequest request, Long requesterId, boolean isAdmin);
    void deleteProject(Long id, Long requesterId, boolean isAdmin);
    ProjectResponse changeState(Long id, ProjectState newState, Long requesterId, boolean isAdmin);
}

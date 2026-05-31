package com.plataforma.projects.repository;

import com.plataforma.projects.model.ProjectMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectMetricRepository extends JpaRepository<ProjectMetric, Long> {

    Page<ProjectMetric> findByProjectId(Long projectId, Pageable pageable);
}

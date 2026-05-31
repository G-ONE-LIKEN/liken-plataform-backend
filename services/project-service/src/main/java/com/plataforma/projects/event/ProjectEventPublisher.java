package com.plataforma.projects.event;

import com.plataforma.projects.model.Project;
import com.plataforma.projects.model.ProjectMetric;
import com.plataforma.projects.model.ProjectState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishProjectCreated(Project project) {
        Map<String, Object> payload = Map.of(
                "projectId", project.getId(),
                "name", project.getName(),
                "ownerId", project.getOwnerId(),
                "energyType", project.getEnergyType().name(),
                "timestamp", LocalDateTime.now().toString()
        );
        kafkaTemplate.send("projects.created", project.getId().toString(), payload);
        log.info("Evento projects.created publicado: projectId={}", project.getId());
    }

    public void publishStateChanged(Project project, ProjectState oldState, ProjectState newState) {
        Map<String, Object> payload = Map.of(
                "projectId", project.getId(),
                "oldState", oldState.name(),
                "newState", newState.name(),
                "timestamp", LocalDateTime.now().toString()
        );
        kafkaTemplate.send("projects.state_changed", project.getId().toString(), payload);
        log.info("Evento projects.state_changed publicado: projectId={} {} -> {}", project.getId(), oldState, newState);
    }

    public void publishMetricsUpdated(ProjectMetric metric) {
        Map<String, Object> payload = Map.of(
                "projectId", metric.getProject().getId(),
                "periodStart", metric.getPeriodStart().toString(),
                "periodEnd", metric.getPeriodEnd().toString(),
                "revenueGenerated", metric.getRevenueGenerated(),
                "timestamp", LocalDateTime.now().toString()
        );
        kafkaTemplate.send("projects.metrics_updated", metric.getProject().getId().toString(), payload);
        log.info("Evento projects.metrics_updated publicado: projectId={}", metric.getProject().getId());
    }
}

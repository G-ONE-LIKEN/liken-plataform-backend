package com.plataforma.projects.event;

import com.plataforma.projects.model.OnChainStatus;
import com.plataforma.projects.model.ProjectState;
import com.plataforma.projects.model.RoundState;
import com.plataforma.projects.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectRoundEventConsumer {

    private final ProjectRepository projectRepository;
    private final ProjectEventPublisher eventPublisher;

    @KafkaListener(topics = "projects.round_finalized", groupId = "service-projects")
    @Transactional
    public void onRoundFinalized(Map<String, Object> payload) {
        String offeringAddress = str(payload.get("offeringContractAddress"));
        if (offeringAddress == null || offeringAddress.isBlank()) {
            log.warn("projects.round_finalized sin offeringContractAddress: {}", payload);
            return;
        }

        projectRepository.findByActiveTrueAndOfferingContractAddressIgnoreCase(offeringAddress)
                .ifPresentOrElse(project -> {
                    ProjectState oldState = project.getState();
                    project.setOnChainStatus(OnChainStatus.DEPLOYED);
                    project.setRoundState(RoundState.FINALIZED);
                    project.setRaisedAmount(decimal(payload.get("totalRaised")));
                    // El state ya pudo haber pasado a OPEN cuando se cruzo el softCap
                    // (ver ProjectService.applyPurchaseAccrual). Solo forzamos OPEN si
                    // todavia estaba en PRE_OPEN.
                    if (oldState == ProjectState.PRE_OPEN) {
                        project.setState(ProjectState.OPEN);
                    }
                    projectRepository.save(project);

                    if (oldState == ProjectState.PRE_OPEN) {
                        eventPublisher.publishStateChanged(project, oldState, ProjectState.OPEN);
                    }
                    log.info("Ronda finalizada reconciliada: projectId={} offering={}", project.getId(), offeringAddress);
                }, () -> log.warn("No pude reconciliar round_finalized. Offering desconocido: {}", offeringAddress));
    }

    @KafkaListener(topics = "projects.round_failed", groupId = "service-projects")
    @Transactional
    public void onRoundFailed(Map<String, Object> payload) {
        String offeringAddress = str(payload.get("offeringContractAddress"));
        if (offeringAddress == null || offeringAddress.isBlank()) {
            log.warn("projects.round_failed sin offeringContractAddress: {}", payload);
            return;
        }

        projectRepository.findByActiveTrueAndOfferingContractAddressIgnoreCase(offeringAddress)
                .ifPresentOrElse(project -> {
                    ProjectState oldState = project.getState();
                    project.setOnChainStatus(OnChainStatus.DEPLOYED);
                    project.setRoundState(RoundState.FAILED);
                    project.setRaisedAmount(decimal(payload.get("totalRaised")));
                    project.setState(ProjectState.CANCELLED);
                    projectRepository.save(project);

                    if (oldState != ProjectState.CANCELLED) {
                        eventPublisher.publishStateChanged(project, oldState, ProjectState.CANCELLED);
                    }
                    log.info("Ronda fallida reconciliada: projectId={} offering={}", project.getId(), offeringAddress);
                }, () -> log.warn("No pude reconciliar round_failed. Offering desconocido: {}", offeringAddress));
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(value.toString());
    }
}

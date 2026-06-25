package com.plataforma.projects.repository;

import com.plataforma.projects.model.EnergyType;
import com.plataforma.projects.model.Project;
import com.plataforma.projects.model.ProjectState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Page<Project> findByActiveTrueAndStateNot(ProjectState excludedState, Pageable pageable);

    Page<Project> findByActiveTrueAndState(ProjectState state, Pageable pageable);

    Page<Project> findByActiveTrueAndStateNotAndEnergyType(ProjectState excludedState, EnergyType energyType,
            Pageable pageable);

    Page<Project> findByActiveTrueAndStateAndEnergyType(ProjectState state, EnergyType energyType, Pageable pageable);

    Optional<Project> findByIdAndActiveTrue(Long id);

    Page<Project> findByActiveTrueAndOwnerId(Long ownerId, Pageable pageable);

    Optional<Project> findByActiveTrueAndOfferingContractAddressIgnoreCase(String offeringContractAddress);

    Optional<Project> findByActiveTrueAndRegistryProjectId(Long registryProjectId);

    List<Project> findByActiveTrueAndOfferingContractAddressIsNotNull();

    List<Project> findByActiveTrueAndStateAndInstalledCapacityMWIsNotNull(ProjectState state);
}

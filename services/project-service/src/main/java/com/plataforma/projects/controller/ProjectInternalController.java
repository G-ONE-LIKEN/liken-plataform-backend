package com.plataforma.projects.controller;

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
}

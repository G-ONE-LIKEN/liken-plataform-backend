package com.plataforma.projects.controller;

import com.plataforma.projects.dto.ActiveProjectOracleDto;
import com.plataforma.projects.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/projects")
@RequiredArgsConstructor
public class InternalProjectController {

    private final ProjectService projectService;

    @GetMapping("/active")
    public ResponseEntity<List<ActiveProjectOracleDto>> listActiveProjectsForOracle() {
        return ResponseEntity.ok(projectService.listActiveProjectsForOracle());
    }
}

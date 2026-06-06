package com.plataforma.blockchain.controller;

import com.plataforma.blockchain.dto.ProjectPublicationCommand;
import com.plataforma.blockchain.service.ProjectPublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/publications")
@RequiredArgsConstructor
public class ProjectPublicationController {

    private final ProjectPublicationService publicationService;

    @PostMapping("/projects")
    public ResponseEntity<Void> publishProject(@RequestBody ProjectPublicationCommand command) {
        publicationService.publishAsync(command);
        return ResponseEntity.accepted().build();
    }
}

package com.plataforma.projects.controller;

import com.plataforma.projects.dto.ApiResponse;
import com.plataforma.projects.dto.DocumentUploadRequest;
import com.plataforma.projects.dto.DocumentUploadResponse;
import com.plataforma.projects.model.ProjectDocument;
import com.plataforma.projects.service.ProjectDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/documents")
@RequiredArgsConstructor
public class ProjectDocumentController {

    private final ProjectDocumentService documentService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('developer:approved')")
    public ResponseEntity<ApiResponse<List<ProjectDocument>>> listDocuments(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success("Documentos obtenidos exitosamente",
                documentService.listDocuments(projectId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('developer:approved')")
    public ResponseEntity<ApiResponse<DocumentUploadResponse>> initiateUpload(
            @PathVariable Long projectId,
            @RequestBody DocumentUploadRequest request,
            Authentication auth) {

        Long requesterId = (Long) auth.getPrincipal();
        boolean isAdmin  = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        DocumentUploadResponse response = documentService.initiateUpload(projectId, request, requesterId, isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("URL de carga generada exitosamente", response));
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('developer:approved')")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable Long projectId,
            @PathVariable Long documentId,
            Authentication auth) {

        Long requesterId = (Long) auth.getPrincipal();
        boolean isAdmin  = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        documentService.deleteDocument(projectId, documentId, requesterId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Documento eliminado exitosamente", null));
    }
}

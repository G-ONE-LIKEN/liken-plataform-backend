package com.plataforma.projects.service;

import com.plataforma.projects.dto.DocumentUploadRequest;
import com.plataforma.projects.dto.DocumentUploadResponse;
import com.plataforma.projects.model.ProjectDocument;

import java.util.List;

public interface ProjectDocumentService {
    List<ProjectDocument> listDocuments(Long projectId);
    DocumentUploadResponse initiateUpload(Long projectId, DocumentUploadRequest request, Long requesterId, boolean isAdmin);
    void deleteDocument(Long projectId, Long documentId, Long requesterId, boolean isAdmin);
}

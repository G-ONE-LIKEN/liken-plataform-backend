package com.plataforma.projects.service.impl;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.SignUrlOption;
import com.plataforma.projects.dto.DocumentUploadRequest;
import com.plataforma.projects.dto.DocumentUploadResponse;
import com.plataforma.projects.exception.ProjectNotFoundException;
import com.plataforma.projects.exception.UnauthorizedProjectAccessException;
import com.plataforma.projects.model.Project;
import com.plataforma.projects.model.ProjectDocument;
import com.plataforma.projects.repository.ProjectDocumentRepository;
import com.plataforma.projects.repository.ProjectRepository;
import com.plataforma.projects.service.ProjectDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Gestion de documentos de proyectos sobre Google Cloud Storage (ver DD014).
 *
 * Para upload usa V4 signed URLs: el cliente sube el archivo directamente a GCS,
 * sin pasar por el servicio. Esto evita usar ancho de banda del backend para
 * mover bytes y permite uploads de archivos grandes.
 */
@Service
@RequiredArgsConstructor
public class ProjectDocumentServiceImpl implements ProjectDocumentService {

    private static final long SIGNED_URL_TTL_MINUTES = 15;

    private final ProjectDocumentRepository documentRepository;
    private final ProjectRepository projectRepository;
    private final Storage storage;

    @Value("${gcp.storage.bucket}")
    private String bucket;

    @Override
    public List<ProjectDocument> listDocuments(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
        return documentRepository.findByProjectId(projectId);
    }

    @Override
    @Transactional
    public DocumentUploadResponse initiateUpload(Long projectId, DocumentUploadRequest request,
                                                 Long requesterId, boolean isAdmin) {
        Project project = projectRepository.findByIdAndActiveTrue(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (!isAdmin && !project.getOwnerId().equals(requesterId)) {
            throw new UnauthorizedProjectAccessException();
        }

        String objectName = "projects/" + projectId + "/" + UUID.randomUUID() + "_" + request.getFileName();

        // Persistir el registro antes de que el cliente suba el archivo
        ProjectDocument doc = ProjectDocument.builder()
                .project(project)
                .documentType(request.getDocumentType())
                .fileName(request.getFileName())
                .s3Key(objectName)
                .mimeType(request.getMimeType())
                .fileSizeBytes(request.getFileSizeBytes())
                .build();
        ProjectDocument saved = documentRepository.save(doc);

        // V4 signed URL: el cliente hace PUT directo a GCS con esta URL
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectName))
                .setContentType(request.getMimeType())
                .build();

        String signedUrl = storage.signUrl(
                blobInfo,
                SIGNED_URL_TTL_MINUTES,
                TimeUnit.MINUTES,
                SignUrlOption.httpMethod(HttpMethod.PUT),
                SignUrlOption.withV4Signature()
        ).toString();

        return DocumentUploadResponse.builder()
                .documentId(saved.getId())
                .presignedUrl(signedUrl)
                .s3Key(objectName)
                .documentType(request.getDocumentType())
                .fileName(request.getFileName())
                .build();
    }

    @Override
    @Transactional
    public void deleteDocument(Long projectId, Long documentId, Long requesterId, boolean isAdmin) {
        Project project = projectRepository.findByIdAndActiveTrue(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (!isAdmin && !project.getOwnerId().equals(requesterId)) {
            throw new UnauthorizedProjectAccessException();
        }

        ProjectDocument doc = documentRepository.findByIdAndProjectId(documentId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado"));

        try {
            storage.delete(BlobId.of(bucket, doc.getS3Key()));
        } catch (Exception ignored) {
            // Si GCS falla en el delete, igual eliminamos el registro
        }
        documentRepository.delete(doc);
    }
}

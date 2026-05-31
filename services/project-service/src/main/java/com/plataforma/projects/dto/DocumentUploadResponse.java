package com.plataforma.projects.dto;

import com.plataforma.projects.model.DocumentType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentUploadResponse {
    private Long documentId;
    private String presignedUrl;
    private String s3Key;
    private DocumentType documentType;
    private String fileName;
}

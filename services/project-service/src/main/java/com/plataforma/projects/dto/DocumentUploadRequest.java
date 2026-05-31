package com.plataforma.projects.dto;

import com.plataforma.projects.model.DocumentType;
import lombok.Data;

@Data
public class DocumentUploadRequest {
    private String fileName;
    private String mimeType;
    private Long fileSizeBytes;
    private DocumentType documentType;
}

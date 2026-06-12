package com.plataforma.user.kyc.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Documento KYC subido por un usuario.
 * El archivo fisico vive en GCS bajo el object name guardado en {@code s3Key}
 * (el field mantiene el nombre legacy por compat — semanticamente es un object
 * name de Google Cloud Storage). Esta tabla guarda solo la metadata y el estado
 * de revision (ver DD013 + DD014).
 */
@Entity
@Table(name = "kyc_documents")
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KycDocumentStatus status = KycDocumentStatus.PENDING;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
    }
}

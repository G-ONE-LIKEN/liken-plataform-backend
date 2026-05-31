package com.plataforma.user.kyc.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.plataforma.shared.exception.UnauthorizedAccessException;
import com.plataforma.shared.exception.UserNotFoundException;
import com.plataforma.user.event.producer.UserContextEventProducer;
import com.plataforma.user.kyc.dto.KycDocumentDto;
import com.plataforma.user.kyc.dto.KycInternalDto;
import com.plataforma.user.kyc.dto.KycStatusResponse;
import com.plataforma.user.kyc.model.KycDocument;
import com.plataforma.user.kyc.model.KycDocumentStatus;
import com.plataforma.user.kyc.repository.KycDocumentRepository;
import com.plataforma.user.model.KycStatus;
import com.plataforma.user.model.User;
import com.plataforma.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de KYC (ver DD013).
 *
 * Flujo:
 *   1. Usuario sube documentos vía POST /api/users/me/kyc → estado pasa a PENDING
 *   2. ADMIN revisa con PUT /api/users/{id}/kyc → estado pasa a APPROVED o REJECTED
 *   3. invest-dividend y marketplace consultan GET /internal/users/{id}/kyc-status
 *      antes de cada operación crítica (compra de tokens, orden marketplace)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {

    private final UserRepository userRepository;
    private final KycDocumentRepository documentRepository;
    private final Storage storage;
    private final UserContextEventProducer contextEventProducer;

    @Value("${gcp.storage.bucket}")
    private String bucket;

    /**
     * Sube documentos KYC del usuario autenticado.
     * Pasa el estado del usuario a PENDING (esperando revisión de ADMIN).
     */
    @Transactional
    public KycStatusResponse uploadDocuments(Long userId, List<MultipartFile> files, List<String> types) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Debes adjuntar al menos un documento");
        }
        if (types == null || types.size() != files.size()) {
            throw new IllegalArgumentException("La lista de tipos debe matchear la cantidad de archivos");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String type = types.get(i);
            // Mantenemos el nombre de campo "s3Key" en la entidad por compat — semánticamente
            // hoy es el object name de GCS. Si en el futuro se renombra, hacerlo en una sola migración.
            String objectName = "kyc/" + userId + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

            try {
                BlobId blobId = BlobId.of(bucket, objectName);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                        .setContentType(file.getContentType())
                        .build();
                storage.createFrom(blobInfo, file.getInputStream());
            } catch (IOException e) {
                throw new RuntimeException("Error subiendo archivo a GCS: " + file.getOriginalFilename(), e);
            }

            documentRepository.save(KycDocument.builder()
                    .userId(userId)
                    .documentType(type)
                    .s3Key(objectName)
                    .status(KycDocumentStatus.PENDING)
                    .build());
        }

        user.setKycStatus(KycStatus.PENDING);
        userRepository.save(user);

        log.info("KYC: usuario {} subió {} documentos. Estado → PENDING", userId, files.size());
        return buildResponse(user);
    }

    @Transactional(readOnly = true)
    public KycStatusResponse getMyKycStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return buildResponse(user);
    }

    @Transactional(readOnly = true)
    public KycInternalDto getInternalStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return KycInternalDto.builder()
                .userId(userId)
                .status(user.getKycStatus())
                .build();
    }

    /**
     * Aprueba o rechaza el KYC de un usuario. Solo ADMIN (validado por @PreAuthorize).
     */
    @Transactional
    public KycStatusResponse review(Long targetUserId, KycStatus decision,
                                    String rejectionReason, Long reviewerId) {
        if (decision != KycStatus.APPROVED && decision != KycStatus.REJECTED) {
            throw new IllegalArgumentException("La decisión debe ser APPROVED o REJECTED");
        }
        if (decision == KycStatus.REJECTED && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new IllegalArgumentException("Si rechazás, debes incluir rejectionReason");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        if (user.getKycStatus() != KycStatus.PENDING) {
            throw new UnauthorizedAccessException(
                    "Solo se puede revisar KYC en estado PENDING. Estado actual: " + user.getKycStatus());
        }

        user.setKycStatus(decision);
        userRepository.save(user);

        // Marcar todos los documentos PENDING del usuario con la misma decisión
        KycDocumentStatus newDocStatus = (decision == KycStatus.APPROVED)
                ? KycDocumentStatus.APPROVED
                : KycDocumentStatus.REJECTED;

        LocalDateTime now = LocalDateTime.now();
        documentRepository.findByUserIdOrderByUploadedAtDesc(targetUserId).stream()
                .filter(d -> d.getStatus() == KycDocumentStatus.PENDING)
                .forEach(d -> {
                    d.setStatus(newDocStatus);
                    d.setReviewedAt(now);
                    d.setReviewedBy(reviewerId);
                    if (decision == KycStatus.REJECTED) {
                        d.setRejectionReason(rejectionReason);
                    }
                    documentRepository.save(d);
                });

        log.info("KYC: usuario {} → {} por reviewer {}", targetUserId, decision, reviewerId);
        contextEventProducer.invalidateContext(targetUserId);
        return buildResponse(user);
    }

    private KycStatusResponse buildResponse(User user) {
        List<KycDocumentDto> docs = documentRepository
                .findByUserIdOrderByUploadedAtDesc(user.getId())
                .stream()
                .map(this::toDto)
                .toList();
        return KycStatusResponse.builder()
                .status(user.getKycStatus())
                .documents(docs)
                .build();
    }

    private KycDocumentDto toDto(KycDocument d) {
        return KycDocumentDto.builder()
                .id(d.getId())
                .documentType(d.getDocumentType())
                .status(d.getStatus())
                .uploadedAt(d.getUploadedAt())
                .reviewedAt(d.getReviewedAt())
                .rejectionReason(d.getRejectionReason())
                .build();
    }
}

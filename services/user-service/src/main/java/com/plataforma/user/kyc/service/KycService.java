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
 * Logica de KYC (ver DD013).
 *
 * Flujo:
 *   1. Usuario sube documentos via POST /api/users/me/kyc → estado pasa a PENDING
 *   2. ADMIN revisa con PUT /api/users/{id}/kyc → estado pasa a APPROVED o REJECTED
 *   3. invest-dividend y marketplace consultan GET /internal/users/{id}/kyc-status
 *      antes de cada operacion critica (compra de tokens, orden marketplace)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {

    private final UserRepository userRepository;
    private final KycDocumentRepository documentRepository;
    private final Storage storage;
    private final UserContextEventProducer contextEventProducer;
  
    private final DiditService diditService;

    @Value("${gcp.storage.bucket}")
    private String bucket;

    /**
     * Sube documentos KYC del usuario autenticado.
     * Pasa el estado del usuario a PENDING (esperando revision de ADMIN).
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
            // Mantenemos el nombre de campo "s3Key" en la entidad por compat — semanticamente
            // hoy es el object name de GCS. Si en el futuro se renombra, hacerlo en una sola migracion.
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

        log.info("KYC: usuario {} subio {} documentos. Estado → PENDING", userId, files.size());
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
            throw new IllegalArgumentException("La decision debe ser APPROVED o REJECTED");
        }
        if (decision == KycStatus.REJECTED && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new IllegalArgumentException("Si rechazas, debes incluir rejectionReason");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        if (user.getKycStatus() != KycStatus.PENDING) {
            throw new UnauthorizedAccessException(
                    "Solo se puede revisar KYC en estado PENDING. Estado actual: " + user.getKycStatus());
        }

        user.setKycStatus(decision);
        userRepository.save(user);

        // Marcar todos los documentos PENDING del usuario con la misma decision
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

    /**
     * Inicia una sesión de verificación KYC automatizada con Didit.
     *
     * Crea la sesión en Didit, guarda el sessionId en el usuario,
     * y devuelve la URL del widget donde el usuario completa el proceso.
     *
     * El estado del usuario NO cambia aquí todavía — cambia cuando
     * Didit llama al webhook con el resultado final.
     *
     * Endpoint: POST /api/users/me/kyc/didit/initiate
     */
    @Transactional
    public String initiateDiditSession(Long userId) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (KycStatus.APPROVED.equals(user.getKycStatus())) {
            throw new IllegalStateException("El usuario ya tiene KYC aprobado");
        }

        // Crear sesión en Didit
        DiditService.DiditSession session = diditService.createSession(String.valueOf(userId));

        // Guardar el sessionId para poder vincular el webhook cuando llegue
        user.setDiditSessionId(session.sessionId());
        // Marcar como PENDING: el usuario inició el proceso
        user.setKycStatus(KycStatus.PENDING);
        userRepository.save(user);

        log.info("KYC Didit: sesión iniciada para userId={} sessionId={}",
                userId, session.sessionId());

        return session.verificationUrl();
    }

    /**
     * Procesa el webhook que Didit envía al terminar una verificación.
     *
     * Didit llama a POST /api/kyc/webhook/didit con el resultado.
     * Este método actualiza el kycStatus del usuario automáticamente,
     * reemplazando la aprobación manual del admin para el flujo Didit.
     *
     * El campo vendor_data contiene nuestro userId (lo pusimos al crear la sesión)
     * como fallback si el sessionId no matchea.
     *
     * @param sessionId    ID de sesión de Didit (campo session_id del webhook)
     * @param status       Resultado: "APPROVED" o "DECLINED"
     * @param vendorData   Nuestro userId interno (campo vendor_data del webhook)
     * @param declineReason Razón del rechazo si status=DECLINED (puede ser null)
     */
    @Transactional
    public void processDiditWebhook(String sessionId, String status,
                                    String vendorData, String declineReason) {
        // Buscar por sessionId primero
        User user = userRepository.findByDiditSessionId(sessionId)
                .orElseGet(() -> {
                    // Fallback: buscar por vendorData (nuestro userId)
                    if (vendorData != null) {
                        try {
                            Long userId = Long.parseLong(vendorData);
                            return userRepository.findById(userId).orElse(null);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    }
                    return null;
                });

        if (user == null) {
            log.warn("KYC Didit webhook: no se encontró usuario para sessionId={} vendorData={}",
                    sessionId, vendorData);
            return;
        }

        log.info("KYC Didit webhook: userId={} sessionId={} status={}",
                user.getId(), sessionId, status);

        switch (status) {
            case "APPROVED" -> {
                user.setKycStatus(KycStatus.APPROVED);
                user.setKycVerifiedAt(java.time.LocalDateTime.now());
                log.info("KYC Didit: usuario {} APROBADO automáticamente", user.getEmail());
                // Invalidar contexto del usuario en el gateway (igual que el flujo manual)
                contextEventProducer.invalidateContext(user.getId());
            }
            case "DECLINED" -> {
                user.setKycStatus(KycStatus.REJECTED);
                log.info("KYC Didit: usuario {} RECHAZADO. Razón: {}",
                        user.getEmail(), declineReason);
            }
            case "IN_REVIEW" -> {
                user.setKycStatus(KycStatus.PENDING);
                log.info("KYC Didit: usuario {} en revisión adicional", user.getEmail());
            }
            default -> log.warn("KYC Didit webhook: estado desconocido '{}' para userId={}",
                    status, user.getId());
        }

        userRepository.save(user);
    }

}

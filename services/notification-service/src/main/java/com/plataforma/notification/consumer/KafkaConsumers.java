package com.plataforma.notification.consumer;

import com.plataforma.notification.client.UserServiceClient;
import com.plataforma.notification.client.ProjectServiceClient;
import com.plataforma.notification.model.NotificationType;
import com.plataforma.notification.service.EmailService;
import com.plataforma.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumers de los eventos Kafka que generan notificaciones.
 *
 * Cada handler:
 *   1) extrae los campos del payload (Map<String,Object>)
 *   2) arma un eventId estable para idempotencia (por defecto usa el id del recurso + tipo)
 *   3) invoca notificationService.notify(...) para cada destinatario
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaConsumers {

    private final NotificationService notificationService;
    private final UserServiceClient userServiceClient;
    private final EmailService emailService;
    private final ProjectServiceClient projectServiceClient;

    // ───────────────────────── PROYECTOS ─────────────────────────

    @KafkaListener(topics = "projects.pending_approval", groupId = "notification-service")
    public void onProjectPendingApproval(Map<String, Object> payload) {
        Long projectId = asLong(payload.get("projectId"));
        String name = String.valueOf(payload.getOrDefault("name", "Proyecto"));
        Long ownerId = asLong(payload.get("ownerId"));
        log.info("[event] projects.pending_approval projectId={} name={}", projectId, name);

        // Notificar a todos los admins
        List<Long> adminIds = userServiceClient.findUserIdsByAudience("ADMINS");
        for (Long adminId : adminIds) {
            notificationService.notify(
                    adminId,
                    NotificationType.ADMIN_PROJECT_PENDING,
                    "Nuevo proyecto pendiente de aprobación",
                    String.format("\"%s\" fue enviado por un developer y espera tu revisión.", name),
                    Map.of("projectId", projectId, "projectName", name, "ownerId", ownerId,
                            "url", "/dashboard/admin/projects/" + projectId),
                    "project-pending-" + projectId,
                    false, // a los admins no spameamos email por cada proyecto nuevo
                    null, null, null
            );
        }
    }

    @KafkaListener(topics = "projects.approved", groupId = "notification-service")
    public void onProjectApproved(Map<String, Object> payload) {
        Long projectId = asLong(payload.get("projectId"));
        Long ownerId   = asLong(payload.get("ownerId"));
        if (ownerId == null) return;
        log.info("[event] projects.approved projectId={}", projectId);

        notificationService.notify(
                ownerId,
                NotificationType.PROJECT_APPROVED,
                "Tu proyecto fue aprobado",
                "Un administrador aprobó tu propuesta. Ya podés avanzar a la etapa de captación.",
                Map.of("projectId", projectId, "url", "/projects/" + projectId),
                "project-approved-" + projectId,
                true,
                "notification",
                "Tu proyecto fue aprobado",
                Map.of("projectId", projectId)
        );
    }

    @KafkaListener(topics = "projects.rejected", groupId = "notification-service")
    public void onProjectRejected(Map<String, Object> payload) {
        Long projectId = asLong(payload.get("projectId"));
        Long ownerId   = asLong(payload.get("ownerId"));
        String reason  = String.valueOf(payload.getOrDefault("reason", ""));
        if (ownerId == null) return;
        log.info("[event] projects.rejected projectId={} reason={}", projectId, reason);

        notificationService.notify(
                ownerId,
                NotificationType.PROJECT_REJECTED,
                "Tu proyecto fue rechazado",
                reason.isBlank()
                        ? "Un administrador rechazó tu propuesta. Revisá los criterios e intentá de nuevo."
                        : "Motivo: " + reason,
                Map.of("projectId", projectId, "reason", reason),
                "project-rejected-" + projectId,
                true,
                "notification",
                "Tu proyecto fue rechazado",
                Map.of("projectId", projectId, "reason", reason)
        );
    }

    // ───────────────────────── USUARIOS ──────────────────────────

    @KafkaListener(topics = "user.registered", groupId = "notification-service")
    public void onUserRegistered(Map<String, Object> payload) {
        Long userId = asLong(payload.get("userId"));
        String email = String.valueOf(payload.getOrDefault("email", ""));
        String firstName = String.valueOf(payload.getOrDefault("firstName", ""));
        boolean emailVerified = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("emailVerified", false)));
        if (userId == null) return;
        log.info("[event] user.registered userId={} email={} emailVerified={}", userId, email, emailVerified);
        if (!emailVerified) {
            log.info("Se omite bienvenida para userId={} porque el email aun no esta verificado", userId);
            return;
        }

        String title = "¡Bienvenido a LIKEN!";
        String body = "Tu cuenta fue creada con éxito. Ya podés explorar proyectos, invertir y seguir el rendimiento de tu portafolio desde tu dashboard.";

        // In-app notification (sin email — lo mandamos abajo con el email del payload
        // para evitar la race condition con la transacción del registro).
        notificationService.notify(
                userId,
                NotificationType.USER_WELCOME,
                title,
                body,
                Map.of("url", "/dashboard"),
                "user-welcome-" + userId,
                false, null, null, null
        );

        // Email directo: usamos el email del payload del evento, no hace falta
        // consultar user-service (que aún puede no haber commiteado la tx).
        if (email != null && !email.isBlank() && !"null".equals(email)) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("firstName", firstName);
            vars.put("title", title);
            vars.put("body", body);
            emailService.send(email, title, "welcome", vars);
        } else {
            log.warn("user.registered sin email en el payload userId={}, no se envía bienvenida", userId);
        }
    }

    @KafkaListener(topics = "user.developer_registered", groupId = "notification-service")
    public void onDeveloperRegistered(Map<String, Object> payload) {
        Long userId = asLong(payload.get("userId"));
        String email = String.valueOf(payload.getOrDefault("email", ""));
        log.info("[event] user.developer_registered userId={} email={}", userId, email);

        List<Long> adminIds = userServiceClient.findUserIdsByAudience("ADMINS");
        for (Long adminId : adminIds) {
            notificationService.notify(
                    adminId,
                    NotificationType.ADMIN_DEVELOPER_PENDING,
                    "Nuevo developer esperando verificación",
                    String.format("%s solicitó ser desarrollador. Revisá su perfil.", email),
                    Map.of("userId", userId, "email", email, "url", "/dashboard/admin/developers"),
                    "dev-registered-" + userId,
                    false, null, null, null
            );
        }
    }

    @KafkaListener(topics = "user.developer_status_changed", groupId = "notification-service")
    public void onDeveloperStatusChanged(Map<String, Object> payload) {
        Long userId  = asLong(payload.get("userId"));
        String status = String.valueOf(payload.getOrDefault("status", ""));
        if (userId == null) return;
        log.info("[event] user.developer_status_changed userId={} status={}", userId, status);

        boolean approved = "APPROVED".equalsIgnoreCase(status);
        String title = approved ? "Tu cuenta de developer fue aprobada" : "Tu cuenta de developer fue rechazada";
        String body  = approved
                ? "Ya podés publicar proyectos en la plataforma."
                : "Revisá los criterios y volvé a aplicar.";

        notificationService.notify(
                userId,
                NotificationType.DEVELOPER_STATUS_CHANGED,
                title,
                body,
                Map.of("status", status),
                "dev-status-" + userId + "-" + status,
                true,
                "notification",
                title,
                Map.of("status", status)
        );
    }

    // ───────────────────────── INVERSIONES ───────────────────────

    @KafkaListener(topics = "investment.token_purchased", groupId = "notification-service")
    public void onTokenPurchased(Map<String, Object> payload) {
        Long userId = asLong(payload.get("userId"));
        if (userId == null) return;

        Object amount = payload.get("usdcAmount");
        Object tokens = payload.get("lknAmount");
        
        String offering = (String) payload.get("offeringContractAddress");
        Long projectId = asLong(payload.get("projectId"));
        if (projectId == null && offering != null) {
            projectId = projectServiceClient.resolveProjectIdByOffering(offering);
        }
        
        log.info("[event] investment.token_purchased userId={} projectId={} offering={}", userId, projectId, offering);

        notificationService.notify(
                userId,
                NotificationType.INVESTMENT_CONFIRMED,
                "Compra de tokens confirmada",
                String.format("Tu inversión de %s USDC en el proyecto #%s fue confirmada.", amount != null ? amount : "0", projectId != null ? projectId : "?"),
                Map.of("projectId", projectId != null ? projectId : 0L, "amount", amount != null ? amount : 0, "tokens", tokens != null ? tokens : 0,
                        "url", projectId != null ? "/projects/" + projectId : "/dashboard"),
                String.valueOf(payload.getOrDefault("eventId", "tx-" + userId + "-" + projectId + "-" + amount)),
                true,
                "notification",
                "Compra de tokens confirmada",
                Map.of("projectId", projectId != null ? projectId : 0L, "amount", amount != null ? amount : 0, "tokens", tokens != null ? tokens : 0)
        );
    }

    @KafkaListener(topics = "dividends.claimed", groupId = "notification-service")
    public void onDividendClaimed(Map<String, Object> payload) {
        Long userId   = asLong(payload.get("userId"));
        Object amount  = payload.get("amount");
        if (userId == null) return;
        log.info("[event] dividends.claimed userId={} amount={}", userId, amount);

        notificationService.notify(
                userId,
                NotificationType.DIVIDEND_RECEIVED,
                "Dividendo reclamado",
                String.format("Tu reclamo de dividendo por %s USDC fue confirmado on-chain.", amount),
                Map.of("amount", amount != null ? amount : 0),
                String.valueOf(payload.getOrDefault("eventId", "div-" + userId + "-" + amount + "-" + System.nanoTime())),
                true,
                "notification",
                "Dividendo reclamado",
                Map.of("amount", amount != null ? amount : 0)
        );
    }

    // ───────────────────────── BILLETERA ─────────────────────────

    @KafkaListener(topics = "wallet.credited", groupId = "notification-service")
    public void onWalletFunded(Map<String, Object> payload) {
        Long userId = asLong(payload.get("userId"));
        Object amount = payload.get("amount");
        if (userId == null) return;
        log.info("[event] wallets.funded userId={}", userId);

        notificationService.notify(
                userId,
                NotificationType.WALLET_FUNDED,
                "Depósito recibido",
                String.format("Tu billetera fue acreditada con %s.", amount),
                Map.of("amount", amount, "url", "/dashboard/wallet"),
                String.valueOf(payload.getOrDefault("eventId", "fund-" + userId + "-" + amount + "-" + System.nanoTime())),
                false, null, null, null
        );
    }

    @KafkaListener(topics = "wallet.debited", groupId = "notification-service")
    public void onWalletDebited(Map<String, Object> payload) {
        Long userId = asLong(payload.get("userId"));
        Object amount = payload.get("amount");
        if (userId == null) return;
        log.info("[event] wallets.debited userId={}", userId);

        notificationService.notify(
                userId,
                NotificationType.WALLET_DEBITED,
                "Retiro procesado",
                String.format("Se debitaron %s de tu billetera.", amount),
                Map.of("amount", amount, "url", "/dashboard/wallet"),
                String.valueOf(payload.getOrDefault("eventId", "debit-" + userId + "-" + amount + "-" + System.nanoTime())),
                false, null, null, null
        );
    }

    private static Long asLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

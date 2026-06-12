package com.plataforma.notification.service;

import com.plataforma.notification.client.UserServiceClient;
import com.plataforma.notification.dto.NotificationResponse;
import com.plataforma.notification.model.Notification;
import com.plataforma.notification.model.NotificationType;
import com.plataforma.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final SseEmitterRegistry sseRegistry;
    private final EmailService emailService;
    private final UserServiceClient userServiceClient;

    /**
     * Crea una notificacion in-app para un usuario, opcionalmente envia email,
     * y la pushea por SSE si el usuario esta conectado.
     *
     * Si externalEventId no es null y ya existe una notificacion para ese par
     * (user, eventId), se ignora la nueva (idempotencia).
     */
    @Transactional
    public void notify(Long userId,
                       NotificationType type,
                       String title,
                       String body,
                       Map<String, Object> data,
                       String externalEventId,
                       boolean withEmail,
                       String emailTemplate,
                       String emailSubject,
                       Map<String, Object> emailVars) {

        if (externalEventId != null && repository.existsByUserIdAndExternalEventId(userId, externalEventId)) {
            log.debug("Notificacion duplicada ignorada: user={} eventId={}", userId, externalEventId);
            return;
        }

        Notification saved = repository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .data(data != null ? data : new HashMap<>())
                .externalEventId(externalEventId)
                .build());

        // Push SSE (best-effort)
        try {
            sseRegistry.push(userId, NotificationResponse.from(saved));
        } catch (Exception ex) {
            log.warn("Error pusheando SSE a user={}: {}", userId, ex.getMessage());
        }

        // Email (best-effort, no rompe la notificacion si falla)
        if (withEmail && emailTemplate != null) {
            userServiceClient.findContact(userId).ifPresent(contact -> {
                Map<String, Object> vars = emailVars != null ? new HashMap<>(emailVars) : new HashMap<>();
                vars.putIfAbsent("firstName", contact.getFirstName());
                vars.putIfAbsent("title", title);
                vars.putIfAbsent("body", body);
                emailService.send(contact.getEmail(), emailSubject, emailTemplate, vars);
                saved.setEmailSentAt(LocalDateTime.now());
                repository.save(saved);
            });
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(Long userId, boolean onlyUnread, Pageable pageable) {
        return onlyUnread
                ? repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public boolean markAsRead(Long id, Long userId) {
        return repository.markAsRead(id, userId, LocalDateTime.now()) > 0;
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return repository.markAllAsRead(userId, LocalDateTime.now());
    }
}

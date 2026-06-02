package com.plataforma.notification.controller;

import com.plataforma.notification.client.UserServiceClient;
import com.plataforma.notification.dto.BroadcastRequest;
import com.plataforma.notification.model.NotificationType;
import com.plataforma.notification.service.NotificationService;
import com.plataforma.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications/broadcast")
@RequiredArgsConstructor
public class BroadcastController {

    private final NotificationService notificationService;
    private final UserServiceClient userServiceClient;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> broadcast(@Valid @RequestBody BroadcastRequest req) {
        List<Long> targets = resolveTargets(req);
        int sent = 0;
        for (Long userId : targets) {
            notificationService.notify(
                    userId,
                    NotificationType.BROADCAST,
                    req.getTitle(),
                    req.getBody(),
                    Map.of("audience", req.getAudience().name()),
                    null, // broadcast no es idempotente — el admin lo dispara explícitamente
                    req.isSendEmail(),
                    "notification",
                    req.getTitle(),
                    Map.of("title", req.getTitle(), "body", req.getBody())
            );
            sent++;
        }
        log.info("Broadcast enviado a {} usuarios. audience={}", sent, req.getAudience());
        return ResponseEntity.ok(ApiResponse.success(Map.of("recipients", sent)));
    }

    private List<Long> resolveTargets(BroadcastRequest req) {
        return switch (req.getAudience()) {
            case USER -> req.getUserId() != null ? List.of(req.getUserId()) : List.of();
            case ALL, ADMINS, DEVELOPERS, INVESTORS ->
                    userServiceClient.findUserIdsByAudience(req.getAudience().name());
        };
    }
}

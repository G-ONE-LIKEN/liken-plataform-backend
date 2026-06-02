package com.plataforma.notification.controller;

import com.plataforma.notification.dto.NotificationResponse;
import com.plataforma.notification.service.NotificationService;
import com.plataforma.notification.service.SseEmitterRegistry;
import com.plataforma.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterRegistry sseRegistry;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> list(
            Authentication auth,
            @RequestParam(defaultValue = "false") boolean unread,
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = (Long) auth.getPrincipal();
        Page<NotificationResponse> page = notificationService.list(userId, unread, pageable)
                .map(NotificationResponse::from);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", notificationService.unreadCount(userId))));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        int updated = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", updated)));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return sseRegistry.register(userId);
    }
}

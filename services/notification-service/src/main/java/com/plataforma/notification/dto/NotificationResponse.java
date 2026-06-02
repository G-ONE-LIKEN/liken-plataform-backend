package com.plataforma.notification.dto;

import com.plataforma.notification.model.Notification;
import com.plataforma.notification.model.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String body;
    private Map<String, Object> data;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .data(n.getData())
                .read(n.getReadAt() != null)
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}

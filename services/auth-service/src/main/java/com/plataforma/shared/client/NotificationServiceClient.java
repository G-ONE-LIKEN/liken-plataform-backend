package com.plataforma.shared.client;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.notification-service.url:http://localhost:8087}")
    private String notificationServiceUrl;

    public void sendTransactionalEmail(
            String to,
            String subject,
            String templateName,
            Map<String, Object> variables) {
        restTemplate.postForObject(
                notificationServiceUrl + "/internal/emails/transactional",
                TransactionalEmailRequest.builder()
                        .to(to)
                        .subject(subject)
                        .templateName(templateName)
                        .variables(variables)
                        .build(),
                Void.class);
    }

    @Data
    @Builder
    public static class TransactionalEmailRequest {
        private String to;
        private String subject;
        private String templateName;
        private Map<String, Object> variables;
    }
}

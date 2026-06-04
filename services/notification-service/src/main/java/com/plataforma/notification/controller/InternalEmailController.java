package com.plataforma.notification.controller;

import com.plataforma.notification.dto.TransactionalEmailRequest;
import com.plataforma.notification.service.EmailService;
import com.plataforma.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/emails")
@RequiredArgsConstructor
public class InternalEmailController {

    private final EmailService emailService;

    @PostMapping("/transactional")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendTransactional(
            @RequestBody TransactionalEmailRequest request) {
        emailService.send(
                request.getTo(),
                request.getSubject(),
                request.getTemplateName(),
                request.getVariables());
        return ResponseEntity.ok(ApiResponse.success("Email procesado", Map.of("status", "accepted")));
    }
}

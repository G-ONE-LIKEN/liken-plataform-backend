package com.plataforma.notification.dto;

import lombok.Data;

import java.util.Map;

@Data
public class TransactionalEmailRequest {
    private String to;
    private String subject;
    private String templateName;
    private Map<String, Object> variables;
}

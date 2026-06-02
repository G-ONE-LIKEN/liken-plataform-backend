package com.plataforma.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${notification.mail.from}")
    private String fromAddress;

    @Value("${notification.mail.from-name}")
    private String fromName;

    @Value("${notification.frontend.base-url}")
    private String frontendUrl;

    public void send(String to, String subject, String templateName, Map<String, Object> variables) {
        if (to == null || to.isBlank()) {
            log.warn("Email destinatario vacío, no se envía. template={}", templateName);
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariables(variables);
            ctx.setVariable("frontendUrl", frontendUrl);
            String html = templateEngine.process(templateName, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("✉  Email enviado [to={}] [from={}] [subject={}] [template={}]",
                    to, fromAddress, subject, templateName);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            log.error("Error enviando email a {} con template {}: {}", to, templateName, ex.getMessage());
        } catch (Exception ex) {
            // No fallar el flujo de notificación in-app si el SMTP no anda
            log.error("Fallo enviando email (no crítico): {}", ex.getMessage());
        }
    }
}

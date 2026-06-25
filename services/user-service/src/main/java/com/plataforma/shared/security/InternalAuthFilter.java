package com.plataforma.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    @Value("${INTERNAL_API_KEY:}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/internal/")) {
            if (path.equals("/internal/kyc/webhook/didit")) {
                // Didit valida su propia firma (HMAC), no usa Internal Token
                filterChain.doFilter(request, response);
                return;
            }

            if (expectedApiKey == null || expectedApiKey.isBlank()) {
                logger.error("INTERNAL_API_KEY no configurada. Rechazando peticion a " + path);
                response.sendError(HttpStatus.FORBIDDEN.value(), "Internal API Key not configured");
                return;
            }

            String providedApiKey = request.getHeader("X-Internal-Token");
            if (!expectedApiKey.equals(providedApiKey)) {
                logger.warn("Peticion rechazada a " + path + ". Token interno invalido o ausente.");
                response.sendError(HttpStatus.FORBIDDEN.value(), "Invalid Internal API Key");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}

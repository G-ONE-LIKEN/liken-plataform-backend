package com.plataforma.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lee los headers inyectados por el API Gateway (DD007) y construye el SecurityContext.
 * No valida JWT — esa responsabilidad es exclusiva del gateway (DD002).
 *
 * Headers esperados (presentes en toda request autenticada):
 *   X-User-Id          — ID numérico del usuario (Long)
 *   X-User-Role        — nombre del rol (ej. "ADMIN", "INVESTOR")
 *   X-User-Permissions — permisos separados por coma (ej. "project:read,project:create")
 */
@Component
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID     = "X-User-Id";
    private static final String HEADER_USER_ROLE   = "X-User-Role";
    private static final String HEADER_PERMISSIONS = "X-User-Permissions";
    private static final String HEADER_TIER        = "X-User-Tier";
    private static final String HEADER_KYC_STATUS  = "X-User-KycStatus";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userIdHeader = request.getHeader(HEADER_USER_ID);

        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                Long userId = Long.parseLong(userIdHeader);

                String role        = request.getHeader(HEADER_USER_ROLE);
                String permissions = request.getHeader(HEADER_PERMISSIONS);

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                if (role != null && !role.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    if ("SUPER_ADMIN".equals(role)) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }
                }
                if (permissions != null && !permissions.isBlank()) {
                    Arrays.stream(permissions.split(","))
                            .map(String::trim)
                            .filter(p -> !p.isEmpty())
                            .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                request.setAttribute(HEADER_TIER, request.getHeader(HEADER_TIER));
                request.setAttribute(HEADER_KYC_STATUS, request.getHeader(HEADER_KYC_STATUS));

            } catch (NumberFormatException e) {
                // Header malformado — no se autentica, Spring Security rechazara la request
            }
        }

        filterChain.doFilter(request, response);
    }
}

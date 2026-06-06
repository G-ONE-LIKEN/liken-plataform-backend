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
 * Lee headers inyectados por el API Gateway y construye el SecurityContext.
 * No valida JWT — eso es responsabilidad exclusiva del gateway.
 *
 * Headers esperados:
 *   X-User-Id, X-User-Role, X-User-Permissions, X-User-Tier, X-User-KycStatus.
 */
@Component
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_PERMISSIONS = "X-User-Permissions";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userIdHeader = request.getHeader(HEADER_USER_ID);
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                Long userId = Long.parseLong(userIdHeader);
                String role = request.getHeader(HEADER_USER_ROLE);
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
            } catch (NumberFormatException e) {
                // Header malformado: deja sin autenticar.
            }
        }
        filterChain.doFilter(request, response);
    }
}

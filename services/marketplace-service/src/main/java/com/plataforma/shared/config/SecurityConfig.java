package com.plataforma.shared.config;

import com.plataforma.shared.security.GatewayHeaderAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    @org.springframework.beans.factory.annotation.Autowired
    private com.plataforma.shared.config.InternalAuthFilter internalAuthFilter;

    private final GatewayHeaderAuthFilter gatewayHeaderAuthFilter;

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers(AntPathRequestMatcher.antMatcher("/internal/**"));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Spring Boot Security activa HTTP Basic y form login por default.
            // Si no los deshabilitas explicitamente, cualquier 401 viaja con
            // `WWW-Authenticate: Basic realm="Realm"` y el browser dispara su
            // popup nativo de "Iniciar sesion". Aca no queremos Basic — la
            // identidad viene en headers desde el gateway, ver `GatewayHeaderAuthFilter`.
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .authorizeHttpRequests(auth -> auth
                // Los endpoints internos se consumen entre servicios dentro de la red privada.
                .requestMatchers(AntPathRequestMatcher.antMatcher("/internal/**")).permitAll()
                .requestMatchers("/actuator/**", "/health").permitAll()
                // Listar ordenes activas es publico (como ver proyectos).
                .requestMatchers("GET", "/api/marketplace/orders").permitAll()
                .anyRequest().authenticated()
            )
            
                .addFilterBefore(internalAuthFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(gatewayHeaderAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

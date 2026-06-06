package com.plataforma.oracle.client;

import com.plataforma.oracle.dto.ActiveProjectOracleDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class ProjectServiceClient {

    private final RestTemplate restTemplate;
    private final String projectServiceUrl;

    public ProjectServiceClient(
            RestTemplateBuilder builder,
            @Value("${project-service.url}") String projectServiceUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(8))
                .build();
        this.projectServiceUrl = projectServiceUrl;
    }

    public List<ActiveProjectOracleDto> listActiveProjects() {
        try {
            ResponseEntity<List<ActiveProjectOracleDto>> response = restTemplate.exchange(
                    projectServiceUrl + "/internal/projects/active",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    });
            return response.getBody() == null ? List.of() : response.getBody();
        } catch (Exception ex) {
            log.error("No se pudo obtener la lista de proyectos activos desde project-service", ex);
            return List.of();
        }
    }
}
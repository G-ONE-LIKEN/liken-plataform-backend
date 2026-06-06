package com.plataforma.projects.service;

import com.plataforma.projects.dto.internal.ProjectPublicationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Slf4j
@Service
public class BlockchainPublicationClient {

    private final RestTemplate restTemplate;
    private final String blockchainServiceUrl;

    public BlockchainPublicationClient(
            RestTemplateBuilder builder,
            @Value("${services.blockchain-service-url}") String blockchainServiceUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.blockchainServiceUrl = blockchainServiceUrl;
    }

    public void requestPublication(ProjectPublicationRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForLocation(
                blockchainServiceUrl + "/internal/publications/projects",
                new HttpEntity<>(request, headers));
        log.info("Publicacion on-chain solicitada: projectId={}", request.getProjectId());
    }
}

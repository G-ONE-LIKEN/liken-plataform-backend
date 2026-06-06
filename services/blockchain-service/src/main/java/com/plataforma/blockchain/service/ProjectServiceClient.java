package com.plataforma.blockchain.service;

import com.plataforma.blockchain.dto.OfferingContractRefResponse;
import com.plataforma.blockchain.dto.ProjectPublicationFailureRequest;
import com.plataforma.blockchain.dto.ProjectPublicationSuccessRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
            @Value("${services.project-service-url}") String projectServiceUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(8))
                .build();
        this.projectServiceUrl = projectServiceUrl;
    }

    public List<OfferingContractRefResponse> listOfferingContracts() {
        ResponseEntity<List<OfferingContractRefResponse>> response = restTemplate.exchange(
                projectServiceUrl + "/internal/projects/offering-contracts",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        return response.getBody() == null ? List.of() : response.getBody();
    }

    public void markPublicationSucceeded(ProjectPublicationSuccessRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForLocation(
                projectServiceUrl + "/internal/projects/publication-success",
                new HttpEntity<>(request, headers));
    }

    public void markPublicationFailed(ProjectPublicationFailureRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForLocation(
                projectServiceUrl + "/internal/projects/publication-failure",
                new HttpEntity<>(request, headers));
    }
}

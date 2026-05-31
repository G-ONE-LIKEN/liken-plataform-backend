package com.plataforma.projects.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Cliente Google Cloud Storage para documentos de proyectos (ver DD014).
 *
 * Misma estrategia de auth que en user-service:
 *   - En GKE: Workload Identity (Application Default Credentials).
 *   - En dev local con fake-gcs-server: emulator host + NoCredentials.
 */
@Configuration
public class GcsConfig {

    @Value("${gcp.project-id:liken-dev}")
    private String projectId;

    @Value("${gcp.storage.emulator-host:}")
    private String emulatorHost;

    @Bean
    public Storage storage() throws IOException {
        if (emulatorHost != null && !emulatorHost.isBlank()) {
            return StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .setHost(emulatorHost)
                    .setCredentials(NoCredentials.getInstance())
                    .build()
                    .getService();
        }

        return StorageOptions.newBuilder()
                .setProjectId(projectId)
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build()
                .getService();
    }
}

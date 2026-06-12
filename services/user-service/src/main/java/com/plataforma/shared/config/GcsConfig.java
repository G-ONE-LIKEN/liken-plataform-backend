package com.plataforma.shared.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Cliente Google Cloud Storage para subida de documentos KYC (ver DD013, DD014).
 *
 * Auth (orden de preferencia):
 *   1. Produccion en GKE: Application Default Credentials (Workload Identity).
 *      El SDK detecta automaticamente las credenciales del Service Account asociado al pod.
 *   2. Desarrollo local apuntando a GCS real: archivo JSON de Service Account en
 *      la env var {@code GOOGLE_APPLICATION_CREDENTIALS}.
 *   3. Desarrollo local con emulador (fake-gcs-server): {@code gcp.storage.emulator-host}
 *      configurado → no requiere credenciales reales, usa {@link NoCredentials}.
 */
@Configuration
public class GcsConfig {

    @Value("${gcp.project-id:liken-dev}")
    private String projectId;

    /**
     * Host del emulador para desarrollo local (ej. {@code http://localhost:4443}).
     * Si esta vacio, el cliente usa GCS real con credenciales por default.
     */
    @Value("${gcp.storage.emulator-host:}")
    private String emulatorHost;

    @Bean
    public Storage storage() throws IOException {
        // Modo emulador: dev local con fake-gcs-server
        if (emulatorHost != null && !emulatorHost.isBlank()) {
            return StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .setHost(emulatorHost)
                    .setCredentials(NoCredentials.getInstance())
                    .build()
                    .getService();
        }

        // Modo prod: Workload Identity (GKE) o GOOGLE_APPLICATION_CREDENTIALS
        return StorageOptions.newBuilder()
                .setProjectId(projectId)
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build()
                .getService();
    }
}

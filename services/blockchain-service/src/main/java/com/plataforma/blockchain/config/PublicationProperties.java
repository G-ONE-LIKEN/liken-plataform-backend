package com.plataforma.blockchain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "publication")
public class PublicationProperties {
    private String contractsWorkspace;
    private String forgeCommand;
    private String scriptEntry;
    private String signerPrivateKey;
    private String platformAdmin;
    private String emisor;
    private String treasury;
    private String deadlineZoneId;
}

package com.plataforma.blockchain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Addresses de los contratos globales que el indexer mira.
 * El {@code OfferingContract} NO esta aca porque hay uno por proyecto — su
 * address viene de la base de project-service.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "contracts")
public class ContractsProperties {
    private String linkenToken;
    private String registry;
    private String distributor;
    private String usdc;
    private String marketplace;
}

package com.plataforma.blockchain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Parámetros del indexer: rango de bloques por petición, intervalo de polling,
 * confirmaciones mínimas.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "web3")
public class Web3Properties {
    private String rpcUrl;
    private long chainId;
    private long pollIntervalSeconds;
    private long maxBlockRange;
    private long confirmations;
}

package com.plataforma.marketplace.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Cliente HTTP contra wallet-service para validar el saldo del comprador
 * antes de concretar una orden de compra P2P.
 */
@Slf4j
@Component
public class WalletClient {

    private final RestTemplate restTemplate;
    private final String walletServiceUrl;

    public WalletClient(
            @Value("${services.wallet-service-url:http://wallet-service:8084}") String walletServiceUrl,
            org.springframework.boot.web.client.RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
        this.walletServiceUrl = walletServiceUrl;
    }

    /**
     * Consulta el saldo del usuario en wallet-service.
     *
     * @return balance disponible (0 si no tiene wallet o si falla la conexión).
     */
    public BigDecimal getUserBalance(Long userId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                    walletServiceUrl + "/internal/wallets/{userId}/balance",
                    Map.class, userId);

            if (response == null) return BigDecimal.ZERO;

            Object balance = response.get("balance");
            if (balance == null) return BigDecimal.ZERO;

            return new BigDecimal(balance.toString());
        } catch (RestClientException e) {
            log.warn("Error consultando balance para userId={}: {}", userId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}

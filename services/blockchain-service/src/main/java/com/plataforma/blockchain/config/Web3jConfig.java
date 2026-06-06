package com.plataforma.blockchain.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

/**
 * Bean del cliente Web3j conectado al RPC configurado (Anvil local o Sepolia).
 */
@Slf4j
@Configuration
public class Web3jConfig {

    @Bean
    public Web3j web3j(@Value("${web3.rpc-url}") String rpcUrl) {
        log.info("Web3j inicializado con RPC: {}", rpcUrl);
        return Web3j.build(new HttpService(rpcUrl));
    }
}

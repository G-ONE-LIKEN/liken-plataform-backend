package com.plataforma.blockchain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Blockchain Service — puente Web2 ↔ Web3.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li><b>Indexar</b> eventos on-chain (LinkenToken, ProjectRegistry,
 *       OfferingContract, DividendDistributor) y publicarlos como mensajes Kafka
 *       que los servicios Web2 consumen.</li>
 *   <li>Mantener un checkpoint del ultimo bloque procesado para reanudar tras restart
 *       sin re-procesar eventos.</li>
 *   <li>Resolver {@code walletAddress → userId} consultando user-service.</li>
 *   <li>Adapter de unidades (USDC 6 dec, LKN 18 dec, precios 6 dec).</li>
 * </ul>
 *
 * <p>NO custodia claves de inversor: las txs {@code buy / approve / claim / refund}
 * las firma el usuario client-side con MetaMask. Las txs administrativas
 * (registerProject, grantRole, depositDividends, deploy de OfferingContract) si
 * pueden firmarse desde este servicio con la cuenta de plataforma — pendiente.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

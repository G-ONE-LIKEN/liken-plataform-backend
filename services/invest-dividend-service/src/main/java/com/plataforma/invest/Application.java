package com.plataforma.invest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Invest-Dividend Service — orquestador de inversiones primarias y dividendos pull.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li><b>Registrar compras primarias</b> consumiendo el evento Kafka
 *       {@code investment.token_purchased} que publica el Blockchain Service.</li>
 *   <li><b>Acumular inversión total por usuario</b> y recalcular el tier
 *       (BRONZE / SILVER / GOLD). Publica {@code user.tier_changed} cuando cambia.</li>
 *   <li><b>Registrar dividendos reclamados</b> consumiendo {@code dividends.claimed}.</li>
 *   <li><b>Preview de compra</b>: calcula cuántos LKN recibe el inversor por una
 *       cantidad de USDC, leyendo el precio vigente de project-service.</li>
 *   <li><b>Pending dividends</b>: lee {@code DividendDistributor.pendingDividends(wallet)}
 *       on-chain con web3j read-only.</li>
 * </ul>
 *
 * <p>NO ejecuta compras: las firma el inversor con MetaMask sobre el
 * {@code OfferingContract}. Tampoco ejecuta {@code depositDividends}: eso queda
 * para una operación admin firmada (fuera del alcance de esta fase).
 */
@SpringBootApplication
// El SecurityConfig + GatewayHeaderAuthFilter viven en `com.plataforma.shared.*`
// (mismo patrón que wallet-service). Sin este scan explícito, Spring Boot solo
// escanea `com.plataforma.invest.*` y deja la security en su default (Basic auth).
@ComponentScan(basePackages = {"com.plataforma.invest", "com.plataforma.shared"})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

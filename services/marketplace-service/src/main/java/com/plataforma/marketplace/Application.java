package com.plataforma.marketplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Marketplace Service — mercado secundario P2P de tokens LKN.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li><b>Publicacion de ordenes de venta</b> por parte de inversores que quieren
 *       vender tokens de un proyecto.</li>
 *   <li><b>Matching de ordenes</b>: algoritmo FIFO con price-time priority, sin
 *       matching parcial (una orden se ejecuta completa o queda OPEN).</li>
 *   <li><b>Publicacion del evento {@code marketplace.order_matched}</b> cuando se
 *       concreta una transaccion P2P. Lo consumen wallet-service (movimientos)
 *       y project-service (actualizacion de holdings).</li>
 *   <li><b>Historial de transacciones</b> del marketplace.</li>
 *   <li><b>Vencimiento automatico</b> de ordenes expiradas ({@code @Scheduled}).</li>
 *   <li><b>Cancelacion reactiva</b> de ordenes si un proyecto cambia de estado
 *       (consume {@code projects.state_changed}).</li>
 * </ul>
 *
 * <p>NO ejecuta transferencias on-chain: el modelo actual es off-chain con
 * validacion de holdings contra project-service. La transferencia real de LKN
 * queda como paso futuro (firma con MetaMask).
 */
@SpringBootApplication
@EnableScheduling
// El SecurityConfig + GatewayHeaderAuthFilter viven en `com.plataforma.shared.*`
// (mismo patron que invest-dividend-service y wallet-service). Sin este scan
// explicito, Spring Boot solo escanea `com.plataforma.marketplace.*` y deja la
// security en su default (Basic auth).
@ComponentScan(basePackages = {"com.plataforma.marketplace", "com.plataforma.shared"})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

package com.plataforma.blockchain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: verifica que el contexto de Spring arranca completo con el perfil
 * de test (H2, Kafka deshabilitado). Atrapa roturas de wireo de beans —el fallo
 * más común al mergear ramas— sin depender de infraestructura externa.
 */
@Tag("unit")
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Test
    void contextLoads() {
        // Si el contexto no arranca, este test falla. No requiere assertions.
    }
}

// src/test/java/com/plataforma/AbstractIntegrationTest.java
package com.plataforma;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clase base para tests de integracion contra Postgres real (docker-compose).
 *
 * Prerequisito:
 *   Tener corriendo `docker compose up -d` antes de ejecutar los tests.
 *
 * @AutoConfigureMockMvc registra el bean MockMvc en el contexto, necesario
 *   para todos los tests que hagan @Autowired MockMvc.
 *   Sin esta anotacion, @SpringBootTest solo levanta el contexto completo
 *   pero no configura el stack MVC de test.
 *
 * @Transactional garantiza rollback al final de cada test,
 *   dejando la DB limpia sin necesidad de resetear el contenedor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@Transactional
public abstract class AbstractIntegrationTest {}
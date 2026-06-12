package com.plataforma.shared.model;

import com.plataforma.rbac.model.Role;
import com.plataforma.shared.model.Auditable;
import com.plataforma.user.model.User;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de auditoria JPA (@PrePersist / @PreUpdate).
 *
 * Usa @DataJpaTest: contexto JPA minimo con H2, sin capa web, sin servicios.
 * NO depende de data.sql ni de ningun seed externo — cada test crea
 * sus propios datos con em.persist() para ser completamente autosuficiente.
 */
@DataJpaTest
@ActiveProfiles("test")
@Tag("unit")
class UserAuditTest
{

	@Autowired
	private TestEntityManager em;

	// ── Helpers estaticos reutilizables desde otros tests ──────────────────────

	public static void assertAuditOnCreate(Auditable entity)
	{
		assertNotNull(
			entity.getCreatedAt(), "createdAt debe setearse en @PrePersist"
		);
		assertNull(
			entity.getUpdatedAt(),
			"updatedAt debe ser null en creacion"
		);
	}

	public static void assertAuditOnUpdate(
		Auditable entity, LocalDateTime createdAt
	)
	{
		assertNotNull(
			entity.getUpdatedAt(),
			"updatedAt debe setearse en @PreUpdate"
		);
		assertTrue(
			entity.getUpdatedAt().isAfter(createdAt),
			"updatedAt debe ser posterior a createdAt"
		);
	}

	private Role persistRole(String nameSuffix)
	{
		Role role = Role.builder()
			.name("TEST_" + nameSuffix)
			.build();
		return em.persist(role);
	}

	// ── Tests ──────────────────────────────────────────────────────────────────

	@Test
	void shouldSetCreatedAtOnPersist()
	{
		Role role = persistRole("CREATED");

		User user = User.builder()
			.email("create@test.com")
			.password("123")
			.role(role)
			.build();

		em.persist(user);
		em.flush();

		assertAuditOnCreate(user);
	}

	@Test
	void shouldSetUpdatedAtOnUpdate() throws InterruptedException
	{
		Role role = persistRole("UPDATED");

		User user = User.builder()
			.email("test@test.com")
			.password("123")
			.role(role)
			.build();

		em.persist(user);
		em.flush();

		LocalDateTime createdAt = user.getCreatedAt();

		user.setPassword("456");

		Thread.sleep(5);

		em.merge(user);
		em.flush();

		assertAuditOnUpdate(user, createdAt);
	}

	@Test
	void allEntitiesShouldExtendAuditable()
	{
		assertTrue(Auditable.class.isAssignableFrom(User.class));
		assertTrue(Auditable.class.isAssignableFrom(Role.class));
	}
}

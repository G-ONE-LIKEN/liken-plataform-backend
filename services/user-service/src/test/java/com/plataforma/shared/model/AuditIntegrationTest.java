package com.plataforma.shared.model;

import com.plataforma.AbstractIntegrationTest;
import com.plataforma.rbac.constant.RoleConstants;
import com.plataforma.rbac.model.Role;
import com.plataforma.rbac.repository.RoleRepository;
import com.plataforma.user.model.User;
import com.plataforma.user.repository.UserRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class AuditIntegrationTest extends AbstractIntegrationTest
{

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Test
	void shouldSetCreatedAtOnUserPersist()
	{
		Role role = roleRepository.findByName(RoleConstants.BASIC)
			.orElseThrow();

		User user = User.builder()
			.email("audit@test.com")
			.password("123")
			.role(role)
			.build();

		User saved = userRepository.save(user);

		assertNotNull(
			saved.getCreatedAt(), "createdAt deberia setearse automaticamente"
		);
		assertNull(
			saved.getUpdatedAt(), "updatedAt deberia ser null en creacion"
		);
	}

	@Test
	void shouldSetUpdatedAtOnUserUpdate() throws InterruptedException
	{
		Role role = roleRepository.findByName(RoleConstants.BASIC)
			.orElseThrow();

		User user = userRepository.save(
			User.builder()
				.email("audit-update@test.com")
				.password("123")
				.role(role)
				.build()
		);

		LocalDateTime createdAt = user.getCreatedAt();

		user.setPassword("456");

		Thread.sleep(5);

		User updated = userRepository.saveAndFlush(user);

		assertNotNull(
			updated.getUpdatedAt(), "updatedAt deberia setearse en update"
		);
		assertTrue(
			updated.getUpdatedAt().isAfter(createdAt),
			"updatedAt deberia ser posterior a createdAt"
		);
	}

	@Test
	void shouldTriggerPreUpdateOnlyOnDirtyEntity()
	{
		Role role = roleRepository.findByName(RoleConstants.BASIC).orElseThrow();

		User user = userRepository.save(
			User.builder().email("dirty@test.com").role(role).build()
		);

		LocalDateTime updatedBefore = user.getUpdatedAt();

		userRepository.save(user);

		assertEquals(
			updatedBefore,
			user.getUpdatedAt(),
			"No deberia actualizar updatedAt si no hubo cambios"
		);
	}

	@Test
	void shouldPersistAuditFieldsInDatabase()
	{
		Role role = roleRepository.findByName(RoleConstants.BASIC)
			.orElseThrow();

		User user = userRepository.save(
			User.builder()
				.email("db@test.com")
				.password("123")
				.role(role)
				.build()
		);

		User reloaded = userRepository.findById(user.getId()).orElseThrow();

		assertNotNull(reloaded.getCreatedAt(), "Debe persistirse en DB");
	}

	@Test
	void shouldApplyAuditToMultipleEntities()
	{
		Role role = roleRepository.save(
			Role.builder()
				.name("TEST_ROLE")
				.build()
		);

		assertNotNull(role.getCreatedAt(), "Role deberia tener auditoria");

		User user = userRepository.save(
			User.builder()
				.email("multi@test.com")
				.password("123")
				.role(role)
				.build()
		);

		assertNotNull(user.getCreatedAt(), "User deberia heredar auditoria");
	}
}

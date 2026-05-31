package com.plataforma.rbac.service;

import com.plataforma.AbstractIntegrationTest;
import com.plataforma.rbac.model.Permission;
import com.plataforma.rbac.model.Role;
import com.plataforma.rbac.repository.PermissionRepository;
import com.plataforma.rbac.repository.RoleRepository;
import com.plataforma.rbac.service.RoleService;
import com.plataforma.shared.exception.RoleNotFoundException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class RolePermissionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private RoleService roleService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;

    @Test
    void shouldUpdateRolePermissionsSuccessfully() {
        Role role = roleRepository.save(Role.builder().name("TEST_ROLE").build());
        Permission p1 = permissionRepository.save(
                Permission.builder().name("test:run").description("desc").build());
        Permission p2 = permissionRepository.save(
                Permission.builder().name("test:stop").description("desc").build());

        Role updated = roleService.updatePermissions(role.getId(), Set.of(p1.getId(), p2.getId()));

        assertEquals(2, updated.getPermissions().size());
        assertTrue(updated.getPermissions().stream().anyMatch(p -> p.getName().equals("test:run")));
    }

    @Test
    void shouldThrowExceptionWhenRoleDoesNotExist() {
        assertThrows(RoleNotFoundException.class,
                () -> roleService.updatePermissions(999L, Set.of(1L)));
    }
}

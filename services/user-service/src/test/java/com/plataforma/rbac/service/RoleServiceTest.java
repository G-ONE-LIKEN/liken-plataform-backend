package com.plataforma.rbac.service;

import com.plataforma.rbac.model.Role;
import com.plataforma.rbac.repository.PermissionRepository;
import com.plataforma.rbac.repository.RoleRepository;
import com.plataforma.rbac.service.RoleService;
import com.plataforma.shared.exception.DuplicateRoleException;
import com.plataforma.shared.exception.UnauthorizedAccessException;
import com.plataforma.user.repository.UserRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class RoleServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;
    @Mock private PermissionRepository permissionRepository;

    @InjectMocks
    private RoleService roleService;

    @Test
    void shouldThrowExceptionWhenDeletingRoleWithAssignedUsers() {
        Long roleId = 1L;
        Role adminRole = Role.builder().id(roleId).name("ADMIN").build();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(adminRole));
        when(userRepository.existsByRole(adminRole)).thenReturn(true);

        UnauthorizedAccessException ex = assertThrows(UnauthorizedAccessException.class,
                () -> roleService.delete(roleId));
        assertEquals("No se puede eliminar un rol que tiene usuarios asignados.", ex.getMessage());
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void shouldDeleteRoleWhenNoUsersAreAssigned() {
        Long roleId = 2L;
        Role emptyRole = Role.builder().id(roleId).name("TEMPORAL").build();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(emptyRole));
        when(userRepository.existsByRole(emptyRole)).thenReturn(false);

        assertDoesNotThrow(() -> roleService.delete(roleId));
        verify(roleRepository, times(1)).delete(emptyRole);
    }

    @Test
    void shouldCreateRoleSuccessfully() {
        when(roleRepository.findByName("ANALYST")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(5L);
            return r;
        });

        Role created = roleService.createRole("analyst", "Analiza métricas");
        assertEquals("ANALYST", created.getName());
        assertNotNull(created.getId());
        verify(roleRepository, times(1)).save(any(Role.class));
    }

    @Test
    void shouldThrowWhenCreatingRoleWithDuplicateName() {
        when(roleRepository.findByName("ADMIN"))
                .thenReturn(Optional.of(Role.builder().id(1L).name("ADMIN").build()));

        assertThrows(DuplicateRoleException.class,
                () -> roleService.createRole("ADMIN", "duplicado"));
        verify(roleRepository, never()).save(any());
    }

    @Test
    void shouldUpdateRoleNameAndDescriptionSuccessfully() {
        Role existing = Role.builder().id(1L).name("BASIC").description("Rol basico").build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roleRepository.findByName("INVESTOR")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        Role updated = roleService.updateRole(1L, "investor", "Puede invertir en proyectos");
        assertEquals("INVESTOR", updated.getName());
        assertEquals("Puede invertir en proyectos", updated.getDescription());
        verify(roleRepository, times(1)).save(existing);
    }

    @Test
    void shouldNotCheckDuplicateWhenNameIsUnchanged() {
        Role existing = Role.builder().id(1L).name("ADMIN").description("descripcion vieja").build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> roleService.updateRole(1L, "ADMIN", "descripcion nueva"));
        verify(roleRepository, never()).findByName(any());
        verify(roleRepository, times(1)).save(existing);
    }

    @Test
    void shouldThrowWhenUpdatingToExistingRoleName() {
        Role basic = Role.builder().id(1L).name("BASIC").build();
        Role admin = Role.builder().id(2L).name("ADMIN").build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(basic));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(admin));

        assertThrows(DuplicateRoleException.class,
                () -> roleService.updateRole(1L, "ADMIN", "cualquier descripcion"));
        verify(roleRepository, never()).save(any());
    }
}

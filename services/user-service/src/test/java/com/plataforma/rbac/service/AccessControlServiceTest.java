package com.plataforma.rbac.service;

import com.plataforma.rbac.constant.RoleConstants;
import com.plataforma.rbac.model.Role;
import com.plataforma.rbac.service.AccessControlService;
import com.plataforma.shared.exception.UnauthorizedAccessException;
import com.plataforma.user.model.User;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class AccessControlServiceTest {

    @InjectMocks
    private AccessControlService accessControlService;

    private Role createRole(String name) {
        return Role.builder().name(name).build();
    }

    private User createUserWithRole(String roleName) {
        return User.builder().role(createRole(roleName)).build();
    }

    @Test
    void testAdminCannotDemoteAdmin() {
        User adminActor  = createUserWithRole(RoleConstants.ADMIN);
        User adminTarget = createUserWithRole(RoleConstants.ADMIN);
        Role investorRol = createRole(RoleConstants.INVESTOR);

        assertThrows(UnauthorizedAccessException.class, () ->
                accessControlService.validateChangeRole(adminActor, adminTarget, investorRol),
                "Un administrador no debe degradar a otro");
    }

    @Test
    void testAdminCanPromoteToDeveloper() {
        User admin    = createUserWithRole(RoleConstants.ADMIN);
        User investor = createUserWithRole(RoleConstants.INVESTOR);
        Role devRole  = createRole(RoleConstants.DEVELOPER);

        assertDoesNotThrow(() ->
                accessControlService.validateChangeRole(admin, investor, devRole));
    }

    @Test
    void investorCannotChangePermissions() {
        User investor = createUserWithRole(RoleConstants.INVESTOR);
        User target   = createUserWithRole(RoleConstants.INVESTOR);
        Role devRole  = createRole(RoleConstants.DEVELOPER);

        assertThrows(UnauthorizedAccessException.class, () ->
                accessControlService.validateChangeRole(investor, target, devRole),
                "Un inversor no puede cambiar permisos");
    }

    @Test
    void basicUserCannotChangePermissions() {
        User basic   = User.builder().email("basic@mail.com").build();
        User target  = createUserWithRole(RoleConstants.INVESTOR);
        Role devRole = createRole(RoleConstants.DEVELOPER);

        assertThrows(UnauthorizedAccessException.class, () ->
                accessControlService.validateChangeRole(basic, target, devRole),
                "Un usuario basico no puede cambiar permisos");
    }

    @Test
    void developerCannotChangePermissions() {
        User developer = createUserWithRole(RoleConstants.DEVELOPER);
        User target    = createUserWithRole(RoleConstants.INVESTOR);
        Role devRole   = createRole(RoleConstants.DEVELOPER);

        assertThrows(UnauthorizedAccessException.class, () ->
                accessControlService.validateChangeRole(developer, target, devRole),
                "Un developer no puede cambiar permisos");
    }
}

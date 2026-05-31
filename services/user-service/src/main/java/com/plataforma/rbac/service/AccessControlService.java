package com.plataforma.rbac.service;

import com.plataforma.rbac.constant.RoleConstants;
import com.plataforma.rbac.model.Role;
import com.plataforma.shared.exception.UnauthorizedAccessException;
import com.plataforma.user.model.User;
import com.plataforma.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final UserRepository userRepository;

    private boolean isSuperAdmin(User user) {
        return user.getRole() != null
                && RoleConstants.SUPER_ADMIN.equals(user.getRole().getName());
    }

    private boolean isAdmin(User user) {
        return user.getRole() != null
                && RoleConstants.ADMIN.equals(user.getRole().getName());
    }

    private boolean isPrivileged(User user) {
        return isAdmin(user) || isSuperAdmin(user);
    }

    public void validateChangeRole(User actor, User target, Role newRole) {
        boolean actorIsSuperAdmin = isSuperAdmin(actor);
        boolean actorIsAdmin      = isAdmin(actor);
        boolean targetIsSuperAdmin = isSuperAdmin(target);
        boolean targetIsAdmin      = isAdmin(target);
        boolean newRoleIsSuperAdmin = RoleConstants.SUPER_ADMIN.equals(newRole.getName());

        // Solo admins o super admins pueden cambiar roles
        if (!actorIsAdmin && !actorIsSuperAdmin)
            throw new UnauthorizedAccessException(
                    "No tenés permisos para cambiar roles.");

        // ADMIN no puede tocar a otros ADMIN ni SUPER_ADMIN
        if (actorIsAdmin && (targetIsAdmin || targetIsSuperAdmin))
            throw new UnauthorizedAccessException(
                    "Solo un Super Admin puede modificar el rol de otro administrador.");

        // Solo SUPER_ADMIN puede asignar el rol SUPER_ADMIN
        if (newRoleIsSuperAdmin && !actorIsSuperAdmin)
            throw new UnauthorizedAccessException(
                    "Solo un Super Admin puede asignar el rol de Super Admin.");

        // SUPER_ADMIN no puede auto-demotear si es el último
        boolean isSelfDemotion = actor.getId() != null
                && actor.getId().equals(target.getId())
                && targetIsSuperAdmin
                && !newRoleIsSuperAdmin;
        if (isSelfDemotion) {
            long superAdminCount = userRepository.countByRole_Name(RoleConstants.SUPER_ADMIN);
            if (superAdminCount <= 1)
                throw new UnauthorizedAccessException(
                        "No podés quitarte el rol de Super Admin si sos el único. Asigná otro Super Admin primero.");
        }
    }
}

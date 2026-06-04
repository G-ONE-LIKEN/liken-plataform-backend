package com.plataforma.shared.config;

import com.plataforma.rbac.constant.PermissionConstants;
import com.plataforma.rbac.constant.RoleConstants;
import com.plataforma.rbac.model.Permission;
import com.plataforma.rbac.model.Role;
import com.plataforma.rbac.repository.PermissionRepository;
import com.plataforma.rbac.repository.RoleRepository;
import com.plataforma.user.model.AuthProvider;
import com.plataforma.user.model.User;
import com.plataforma.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Siembra permisos, roles y un usuario admin inicial en el arranque (perfil dev).
 * Las migraciones Flyway garantizan el mismo estado en otros entornos.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed-admin:true}")
    private boolean seedAdmin;

    @Value("${app.seed-admin-email-1:local-admin-1@example.test}")
    private String seedAdminEmail1;

    @Value("${app.seed-admin-password-1:change-me-admin-password}")
    private String seedAdminPassword1;

    @Value("${app.seed-admin-email-2:local-admin-2@example.test}")
    private String seedAdminEmail2;

    @Value("${app.seed-admin-password-2:change-me-admin-password-2}")
    private String seedAdminPassword2;

    @Override
    public void run(String... args) {
        Permission readProject   = seed(PermissionConstants.PROJECT_READ);
        Permission createProject = seed(PermissionConstants.PROJECT_CREATE);
        Permission updateProject = seed(PermissionConstants.PROJECT_UPDATE);
        Permission deleteProject = seed(PermissionConstants.PROJECT_DELETE);
        Permission investCreate  = seed(PermissionConstants.INVEST_CREATE);
        Permission readUser      = seed(PermissionConstants.USER_READ);
        Permission updateUser    = seed(PermissionConstants.USER_UPDATE);
        Permission deleteUser    = seed(PermissionConstants.USER_DELETE);
        Permission kycReview     = seed(PermissionConstants.KYC_REVIEW);

        Set<Permission> allPermissions = Set.of(
                readProject, createProject, updateProject, deleteProject,
                investCreate, readUser, updateUser, deleteUser, kycReview);

        seedRole(RoleConstants.BASIC,       Set.of(readProject));
        seedRole(RoleConstants.INVESTOR,    Set.of(readProject, investCreate));
        seedRole(RoleConstants.DEVELOPER,   Set.of(readProject, createProject, updateProject));
        seedRole(RoleConstants.ADMIN,       allPermissions);
        seedRole(RoleConstants.SUPER_ADMIN, allPermissions);

        if (seedAdmin) {
            seedSuperAdminUser(seedAdminEmail1, seedAdminPassword1);
            seedSuperAdminUser(seedAdminEmail2, seedAdminPassword2);
        }
    }

    private Permission seed(String name) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> permissionRepository.save(
                        Permission.builder()
                                .name(name)
                                .description("Permiso para " + name)
                                .build()));
    }

    private void seedRole(String name, Set<Permission> permissions) {
        if (roleRepository.findByName(name).isEmpty())
            roleRepository.save(Role.builder().name(name).permissions(permissions).build());
    }

    private void seedSuperAdminUser(String email, String password) {
        Role superAdminRole = roleRepository.findByName(RoleConstants.SUPER_ADMIN)
                .orElseThrow(() -> new RuntimeException("Rol SUPER_ADMIN no encontrado"));
        userRepository.findByEmail(email).ifPresentOrElse(existing -> {
            boolean changed = false;
            if (!existing.isEmailVerified()) {
                existing.setEmailVerified(true);
                changed = true;
            }
            if (!existing.isActive()) {
                existing.setActive(true);
                changed = true;
            }
            if (!existing.isProfileCompleted()) {
                existing.setProfileCompleted(true);
                changed = true;
            }
            if (existing.getAuthProvider() == null) {
                existing.setAuthProvider(AuthProvider.LOCAL);
                changed = true;
            }
            if (existing.getRole() == null || !RoleConstants.SUPER_ADMIN.equals(existing.getRole().getName())) {
                existing.setRole(superAdminRole);
                changed = true;
            }
            if (changed) {
                userRepository.save(existing);
            }
        }, () -> userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(superAdminRole)
                .active(true)
                .profileCompleted(true)
                .emailVerified(true)
                .authProvider(AuthProvider.LOCAL)
                .build()));
    }
}

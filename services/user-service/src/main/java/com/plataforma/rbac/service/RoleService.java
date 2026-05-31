package com.plataforma.rbac.service;

import com.plataforma.rbac.model.Permission;
import com.plataforma.rbac.model.Role;
import com.plataforma.rbac.repository.PermissionRepository;
import com.plataforma.rbac.repository.RoleRepository;
import com.plataforma.shared.exception.DuplicateRoleException;
import com.plataforma.shared.exception.RoleNotFoundException;
import com.plataforma.shared.exception.UnauthorizedAccessException;
import com.plataforma.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    public List<Role> findAll() { return roleRepository.findAll(); }

    public Role findById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new RoleNotFoundException("Rol no encontrado"));
    }

    @Transactional
    public Role createRole(String name, String description) {
        String normalized = name.toUpperCase().trim();
        if (roleRepository.findByName(normalized).isPresent())
            throw new DuplicateRoleException(normalized);
        return roleRepository.save(Role.builder()
                .name(normalized)
                .description(description)
                .build());
    }

    @Transactional
    public Role updateRole(Long id, String newName, String newDescription) {
        Role role = findById(id);
        String normalized = newName.toUpperCase().trim();
        boolean nameChanged = !role.getName().equals(normalized);
        if (nameChanged && roleRepository.findByName(normalized).isPresent())
            throw new DuplicateRoleException(normalized);
        role.setName(normalized);
        role.setDescription(newDescription);
        return roleRepository.save(role);
    }

    @Transactional
    public void delete(Long id) {
        Role role = findById(id);
        if (userRepository.existsByRole(role))
            throw new UnauthorizedAccessException(
                    "No se puede eliminar un rol que tiene usuarios asignados.");
        roleRepository.delete(role);
    }

    @Transactional
    public Role updatePermissions(Long roleId, Set<Long> permissionIds) {
        Role role = findById(roleId);
        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        role.setPermissions(new HashSet<>(permissions));
        return roleRepository.save(role);
    }
}

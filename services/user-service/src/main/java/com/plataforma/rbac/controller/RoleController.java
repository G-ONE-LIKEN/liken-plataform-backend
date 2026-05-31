package com.plataforma.rbac.controller;

import com.plataforma.rbac.dto.RoleRequest;
import com.plataforma.rbac.model.Role;
import com.plataforma.rbac.service.RoleService;
import com.plataforma.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Role>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Roles obtenidos", roleService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Role>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Rol encontrado", roleService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Role>> create(@RequestBody RoleRequest request) {
        Role created = roleService.createRole(request.getName(), request.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Rol creado", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Role>> update(
            @PathVariable Long id, @RequestBody RoleRequest request) {
        Role updated = roleService.updateRole(id, request.getName(), request.getDescription());
        return ResponseEntity.ok(ApiResponse.success("Rol actualizado", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Rol eliminado", null));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Role>> updatePermissions(
            @PathVariable Long id, @RequestBody Set<Long> permissionIds) {
        Role updated = roleService.updatePermissions(id, permissionIds);
        return ResponseEntity.ok(ApiResponse.success("Permisos actualizados", updated));
    }
}

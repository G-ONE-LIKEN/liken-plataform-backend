package com.plataforma.user.controller;

import com.plataforma.rbac.model.Permission;
import com.plataforma.rbac.model.Role;
import com.plataforma.rbac.service.AccessControlService;
import com.plataforma.shared.dto.ApiResponse;
import com.plataforma.shared.exception.RoleNotFoundException;
import com.plataforma.shared.exception.UserNotFoundException;
import com.plataforma.user.dto.UpdateUserRequest;
import com.plataforma.user.dto.ProfileRequest;
import com.plataforma.user.dto.UserContextDTO;
import com.plataforma.user.dto.UserRequest;
import com.plataforma.user.dto.WalletLinkRequest;
import com.plataforma.user.model.DeveloperStatus;
import com.plataforma.user.model.User;
import com.plataforma.user.service.UserService;
import com.plataforma.user.service.WalletLinkingService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccessControlService accessControlService;
    private final WalletLinkingService walletLinkingService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserContextDTO>> getMe(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.getUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return ResponseEntity.ok(ApiResponse.success("OK", toContextDto(user)));
    }

    @GetMapping("/me/profile")
    public ResponseEntity<ApiResponse<UserContextDTO>> getMyProfile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.getUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return ResponseEntity.ok(ApiResponse.success("Perfil obtenido", toContextDto(user)));
    }

    @PutMapping("/me/profile")
    public ResponseEntity<ApiResponse<UserContextDTO>> completeMyProfile(
            Authentication authentication,
            @RequestBody ProfileRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        User updated = userService.completeProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Perfil actualizado", toContextDto(updated)));
    }

    // ── Vinculo wallet on-chain (custodia hibrida) ─────────────────────────

    /**
     * Devuelve un nonce + el mensaje exacto que el usuario debe firmar con MetaMask.
     * El nonce vive 5 min. Si vence, hay que pedir otro.
     */
    @PostMapping("/me/wallet/nonce")
    public ResponseEntity<ApiResponse<Map<String, String>>> requestWalletNonce(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String nonce = walletLinkingService.generateNonce(userId);
        String message = walletLinkingService.buildMessage(nonce);
        return ResponseEntity.ok(ApiResponse.success(
                "Nonce generado. Firma el mensaje con tu wallet.",
                Map.of("nonce", nonce, "message", message)));
    }

    /**
     * Verifica la firma con ecrecover y persiste la wallet vinculada al usuario.
     */
    @PostMapping("/me/wallet")
    public ResponseEntity<ApiResponse<UserContextDTO>> linkWallet(
            Authentication authentication,
            @RequestBody WalletLinkRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        User updated = walletLinkingService.linkWallet(
                userId, request.getWalletAddress(), request.getSignature());
        return ResponseEntity.ok(ApiResponse.success("Wallet vinculada", toContextDto(updated)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:update')")
    public ResponseEntity<ApiResponse<User>> create(@RequestBody UserRequest request) {
        User toCreate = User.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dni(request.getDni())
                .birthDate(request.getBirthDate())
                .phone(request.getPhone())
                .country(request.getCountry())
                .province(request.getProvince())
                .address(request.getAddress())
                .termsAccepted(request.isTermsAccepted())
                .build();
        User created = userService.registerUser(toCreate, request.getRoleName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Usuario creado", created));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:read')")
    public ResponseEntity<ApiResponse<Page<User>>> getAll(Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success("Usuarios obtenidos", userService.getAllUsers(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:read')")
    public ResponseEntity<ApiResponse<User>> getById(@PathVariable Long id) {
        User user = userService.getUserById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return ResponseEntity.ok(ApiResponse.success("Usuario encontrado", user));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:update')")
    public ResponseEntity<ApiResponse<User>> update(
            @PathVariable Long id, @RequestBody UpdateUserRequest request) {
        User details = User.builder()
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dni(request.getDni())
                .birthDate(request.getBirthDate())
                .phone(request.getPhone())
                .country(request.getCountry())
                .province(request.getProvince())
                .address(request.getAddress())
                .termsAccepted(Boolean.TRUE.equals(request.getTermsAccepted()))
                .build();
        User updated = userService.updateUser(id, details);
        return ResponseEntity.ok(ApiResponse.success("Usuario actualizado", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:delete')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.success("Usuario desactivado", null));
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:update')")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        userService.activateUser(id);
        return ResponseEntity.ok(ApiResponse.success("Usuario activado", null));
    }

    @GetMapping("/developers")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<User>>> getDevelopers(
            @RequestParam(required = false) DeveloperStatus status,
            Pageable pageable) {
        Page<User> developers = status != null
                ? userService.getDevelopersByStatus(status, pageable)
                : userService.getAllDevelopers(pageable);
        return ResponseEntity.ok(ApiResponse.success("Desarrolladores obtenidos", developers));
    }

    @PutMapping("/{id}/developer-status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<User>> updateDeveloperStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        DeveloperStatus status = DeveloperStatus.valueOf(body.get("status"));
        User updated = userService.updateDeveloperStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Estado actualizado", updated));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:update')")
    public ResponseEntity<ApiResponse<User>> assignRole(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            Authentication authentication) {
        Long roleId = body.get("roleId");
        Long actorId = (Long) authentication.getPrincipal();
        User actor = userService.getUserById(actorId)
                .orElseThrow(() -> new UserNotFoundException(actorId));
        User target = userService.getUserById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        Role role = userService.getRoleById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Rol no encontrado: " + roleId));
        accessControlService.validateChangeRole(actor, target, role);
        User updated = userService.assignRole(target, role);
        return ResponseEntity.ok(ApiResponse.success("Rol asignado", updated));
    }

    private UserContextDTO toContextDto(User user) {
        List<String> permissions = (user.getRole() != null && user.getRole().getPermissions() != null)
                ? user.getRole().getPermissions().stream()
                        .map(Permission::getName)
                        .collect(Collectors.toList())
                : List.of();
        return UserContextDTO.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .dni(user.getDni())
                .birthDate(user.getBirthDate())
                .phone(user.getPhone())
                .country(user.getCountry())
                .province(user.getProvince())
                .address(user.getAddress())
                .termsAccepted(user.isTermsAccepted())
                .profileCompleted(user.isProfileCompleted())
                .emailVerified(user.isEmailVerified())
                .authProvider(user.getAuthProvider() != null ? user.getAuthProvider().name() : null)
                .pictureUrl(user.getPictureUrl())
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .permissions(permissions)
                .tier(user.getTier())
                .kycStatus(user.getKycStatus())
                .developerStatus(user.getDeveloperStatus())
                .walletAddress(user.getWalletAddress())
                .build();
    }
}

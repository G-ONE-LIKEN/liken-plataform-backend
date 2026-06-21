package com.plataforma.user.controller;

import com.plataforma.rbac.model.Permission;
import com.plataforma.shared.exception.UnauthorizedAccessException;
import com.plataforma.shared.exception.UserNotFoundException;
import com.plataforma.user.dto.GoogleUserRequest;
import com.plataforma.user.dto.LocalUserRegistrationRequest;
import com.plataforma.user.dto.UserContextDTO;
import com.plataforma.user.dto.UserInternalDTO;
import com.plataforma.user.model.User;
import com.plataforma.user.repository.UserRepository;
import com.plataforma.user.service.UserService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping("/by-email/{email}")
    public ResponseEntity<UserInternalDTO> findByEmail(@PathVariable String email) {
        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + email));
        return ResponseEntity.ok(toDto(user));
    }

    @PostMapping("/google")
    public ResponseEntity<UserInternalDTO> createGoogleUser(@RequestBody GoogleUserRequest request) {
        User toCreate = User.builder()
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .googleSubject(request.getGoogleSubject())
                .pictureUrl(request.getPictureUrl())
                .build();
        return ResponseEntity.ok(toDto(userService.registerGoogleUser(toCreate, request.getRoleName())));
    }

    @PostMapping("/local")
    public ResponseEntity<UserInternalDTO> createLocalUser(@RequestBody LocalUserRegistrationRequest request) {
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
        return ResponseEntity.ok(toDto(userService.registerVerifiedLocalUser(toCreate, request.getRoleName())));
    }

    @PutMapping("/{id}/google")
    public ResponseEntity<UserInternalDTO> linkGoogleSubject(
            @PathVariable Long id,
            @RequestBody GoogleUserRequest request) {
        return ResponseEntity.ok(toDto(userService.linkGoogleSubject(
                id,
                request.getGoogleSubject(),
                request.getPictureUrl())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserInternalDTO> findById(@PathVariable Long id) {
        User user = userService.getUserById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return ResponseEntity.ok(toDto(user));
    }

    /**
     * Lookup wallet→user usado por el Blockchain Service para enriquecer eventos
     * on-chain con el {@code userId} resuelto antes de publicar a Kafka.
     *
     * <p>Devuelve {@code {"userId": <Long>}} si la wallet esta vinculada, o 404
     * si no. Insensible al casing (la wallet se guarda en EIP-55, normalizamos
     * para la comparacion).
     */
    @GetMapping("/by-wallet/{address}")
    public ResponseEntity<java.util.Map<String, Long>> findByWallet(@PathVariable String address) {
        return userRepository.findByWalletAddressIgnoreCase(address)
                .map(u -> ResponseEntity.ok(java.util.Map.of("userId", u.getId())))
                .orElseThrow(() -> new UserNotFoundException(
                        "Wallet no vinculada a ningun usuario: " + address));
    }

    @GetMapping("/{id}/context")
    public ResponseEntity<UserContextDTO> getContext(@PathVariable Long id) {
        User user = userService.getUserById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        if (!user.isActive()) {
            throw new UnauthorizedAccessException("Usuario inactivo");
        }
        List<String> permissions = (user.getRole() != null && user.getRole().getPermissions() != null)
                ? user.getRole().getPermissions().stream()
                        .map(Permission::getName)
                        .collect(Collectors.toList())
                : List.of();
        return ResponseEntity.ok(UserContextDTO.builder()
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
                .build());
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> updatePassword(
            @PathVariable Long id,
            @RequestBody String encodedPassword) {
        userService.updatePassword(id, encodedPassword);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/email-verified")
    public ResponseEntity<UserInternalDTO> markEmailVerified(@PathVariable Long id) {
        return ResponseEntity.ok(toDto(userService.markEmailVerified(id)));
    }

    /**
     * Endpoint usado por notification-service para componer emails.
     * Devuelve email + nombre del usuario.
     */
    @GetMapping("/{id}/contact")
    public ResponseEntity<UserContactDTO> getContact(@PathVariable Long id) {
        User user = userService.getUserById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return ResponseEntity.ok(UserContactDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .build());
    }

    /**
     * Devuelve los ids de los usuarios que matchean una audiencia para broadcasts
     * y notificaciones automaticas. Audiencias soportadas:
     *   ALL          → todos los usuarios activos
     *   ADMINS       → ADMIN + SUPER_ADMIN activos
     *   DEVELOPERS   → DEVELOPER activos
     *   INVESTORS    → todos los activos cuyo rol no sea admin ni developer
     */
    @GetMapping("/by-audience")
    public ResponseEntity<List<Long>> findByAudience(@RequestParam String audience) {
        List<User> users = switch (audience.toUpperCase()) {
            case "ALL"        -> userRepository.findByActiveTrue();
            case "ADMINS"     -> userRepository.findByRole_NameIn(List.of("ADMIN", "SUPER_ADMIN"));
            case "DEVELOPERS" -> userRepository.findByRole_NameIn(List.of("DEVELOPER"));
            case "INVESTORS"  -> userRepository.findByActiveTrue().stream()
                    .filter(u -> u.getRole() == null
                            || !List.of("ADMIN", "SUPER_ADMIN", "DEVELOPER").contains(u.getRole().getName()))
                    .toList();
            default -> List.of();
        };
        return ResponseEntity.ok(users.stream()
                .filter(User::isActive)
                .map(User::getId)
                .toList());
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserContactDTO {
        private Long id;
        private String email;
        private String firstName;
        private String lastName;
        private String role;
    }

    private UserInternalDTO toDto(User user) {
        List<String> permissions = (user.getRole() != null && user.getRole().getPermissions() != null)
                ? user.getRole().getPermissions().stream()
                        .map(Permission::getName)
                        .collect(Collectors.toList())
                : List.of();

        return UserInternalDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .password(user.getPassword())
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
                .googleSubject(user.getGoogleSubject())
                .pictureUrl(user.getPictureUrl())
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .permissions(permissions)
                .active(user.isActive())
                .build();
    }
}

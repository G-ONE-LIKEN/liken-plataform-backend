package com.plataforma.user.controller;

import com.plataforma.rbac.model.Permission;
import com.plataforma.shared.exception.UnauthorizedAccessException;
import com.plataforma.shared.exception.UserNotFoundException;
import com.plataforma.user.dto.GoogleUserRequest;
import com.plataforma.user.dto.UserContextDTO;
import com.plataforma.user.dto.UserInternalDTO;
import com.plataforma.user.model.User;
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
                .authProvider(user.getAuthProvider() != null ? user.getAuthProvider().name() : null)
                .pictureUrl(user.getPictureUrl())
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .permissions(permissions)
                .tier(user.getTier())
                .kycStatus(user.getKycStatus())
                .developerStatus(user.getDeveloperStatus())
                .build());
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> updatePassword(
            @PathVariable Long id,
            @RequestBody String encodedPassword) {
        userService.updatePassword(id, encodedPassword);
        return ResponseEntity.noContent().build();
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
                .authProvider(user.getAuthProvider() != null ? user.getAuthProvider().name() : null)
                .googleSubject(user.getGoogleSubject())
                .pictureUrl(user.getPictureUrl())
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .permissions(permissions)
                .active(user.isActive())
                .build();
    }
}

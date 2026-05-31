package com.plataforma.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.plataforma.rbac.model.Role;
import com.plataforma.shared.model.Auditable;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    @JsonIgnore
    private String password;

    @Column(name = "first_name", length = 120)
    private String firstName;

    @Column(name = "last_name", length = 120)
    private String lastName;

    @Column(unique = true, length = 20)
    private String dni;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 40)
    private String phone;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String province;

    @Column(length = 255)
    private String address;

    @Column(name = "terms_accepted", nullable = false)
    @Builder.Default
    private boolean termsAccepted = false;

    @Column(name = "profile_completed", nullable = false)
    @Builder.Default
    private boolean profileCompleted = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

    @Column(name = "picture_url", length = 500)
    private String pictureUrl;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Builder.Default
    private boolean active = true;

    /**
     * Nivel del usuario (BASIC/SILVER/GOLD). Default BASIC al registrarse.
     * Se updatea por evento user.tier_changed publicado por invest-dividend-service.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Tier tier = Tier.BRONZE;

    /**
     * Estado del proceso KYC. Default NOT_STARTED al registrarse.
     * Las operaciones críticas (inversión, marketplace) requieren APPROVED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.NOT_STARTED;

    /**
     * Estado de verificación del desarrollador. Solo aplica a usuarios con rol DEVELOPER.
     * Un developer debe ser APPROVED por un admin antes de poder publicar proyectos.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "developer_status", length = 20)
    private DeveloperStatus developerStatus;

    public boolean hasPermission(String permissionName) {
        if (role == null || role.getPermissions() == null) return false;
        return role.getPermissions().stream()
                .anyMatch(p -> p.getName().equals(permissionName));
    }
}

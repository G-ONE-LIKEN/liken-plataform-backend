package com.plataforma.user.dto;

import com.plataforma.user.model.DeveloperStatus;
import com.plataforma.user.model.KycStatus;
import com.plataforma.user.model.Tier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserContextDTO {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String dni;
    private LocalDate birthDate;
    private String phone;
    private String country;
    private String province;
    private String address;
    private boolean termsAccepted;
    private boolean profileCompleted;
    private boolean emailVerified;
    private String authProvider;
    private String pictureUrl;
    private String role;
    private List<String> permissions;
    private Tier tier;
    private KycStatus kycStatus;
    private DeveloperStatus developerStatus;
    /** Wallet on-chain vinculada (EIP-55), o null si el usuario no vinculó todavía. */
    private String walletAddress;
}

package com.plataforma.auth.service;

import com.plataforma.shared.client.UserServiceClient;
import com.plataforma.shared.client.dto.GoogleUserRequest;
import com.plataforma.shared.client.dto.UserAuthDTO;
import com.plataforma.shared.exception.UnauthorizedAccessException;
import com.plataforma.shared.security.JwtUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Credenciales invalidas.";

    private final UserServiceClient userServiceClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final GoogleTokenService googleTokenService;

    @Getter
    public static class LoginResult {
        private final Long userId;
        private final String accessToken;

        public LoginResult(Long userId, String accessToken) {
            this.userId = userId;
            this.accessToken = accessToken;
        }
    }

    public String login(String email, String password) {
        UserAuthDTO user = authenticate(email, password);
        return jwtUtils.generateToken(user);
    }

    public LoginResult loginFull(String email, String password) {
        UserAuthDTO user = authenticate(email, password);
        String accessToken = jwtUtils.generateToken(user);
        return new LoginResult(user.getId(), accessToken);
    }

    public LoginResult loginWithGoogle(String idToken, String roleName) {
        GoogleTokenPayload payload = googleTokenService.verify(idToken);
        UserAuthDTO user;
        try {
            user = userServiceClient.findByEmail(payload.getEmail());
            if (!user.isActive()) {
                throw new UnauthorizedAccessException("Tu cuenta esta desactivada.");
            }
            if (user.getGoogleSubject() == null || user.getGoogleSubject().isBlank()) {
                user = userServiceClient.linkGoogleSubject(user.getId(), toGoogleUserRequest(payload, roleName));
            } else if (!user.getGoogleSubject().equals(payload.getSubject())) {
                throw new UnauthorizedAccessException("El email ya esta vinculado a otra cuenta de Google.");
            }
        } catch (UnauthorizedAccessException ex) {
            if (!isInvalidCredentials(ex)) {
                throw ex;
            }
            user = userServiceClient.createGoogleUser(toGoogleUserRequest(payload, roleName));
        }
        String accessToken = jwtUtils.generateToken(user);
        return new LoginResult(user.getId(), accessToken);
    }

    public String refresh(String refreshToken) {
        Long userId = refreshTokenService.validate(refreshToken)
                .orElseThrow(() -> new UnauthorizedAccessException("Refresh token invalido o expirado."));

        UserAuthDTO user = userServiceClient.findById(userId);

        if (!user.isActive()) {
            throw new UnauthorizedAccessException("Tu cuenta esta desactivada.");
        }

        return jwtUtils.generateToken(user);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    public void revokeUserTokens(Long userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        UserAuthDTO user = userServiceClient.findById(userId);

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new UnauthorizedAccessException("Ingresa con Google o configura una contrasena.");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new UnauthorizedAccessException("La contrasena actual es incorrecta.");
        }

        userServiceClient.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    private UserAuthDTO authenticate(String email, String password) {
        UserAuthDTO user = userServiceClient.findByEmail(email);

        if (!user.isActive()) {
            throw new UnauthorizedAccessException("Tu cuenta esta desactivada.");
        }

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new UnauthorizedAccessException("Ingresa con Google o configura una contrasena.");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new UnauthorizedAccessException(INVALID_CREDENTIALS);
        }

        return user;
    }

    private GoogleUserRequest toGoogleUserRequest(GoogleTokenPayload payload, String roleName) {
        return GoogleUserRequest.builder()
                .email(payload.getEmail())
                .firstName(payload.getFirstName())
                .lastName(payload.getLastName())
                .googleSubject(payload.getSubject())
                .pictureUrl(payload.getPictureUrl())
                .roleName(roleName)
                .build();
    }

    private boolean isInvalidCredentials(UnauthorizedAccessException ex) {
        String message = ex.getMessage();
        return message != null && (message.contains("Credenciales inv") || message.equals(INVALID_CREDENTIALS));
    }
}

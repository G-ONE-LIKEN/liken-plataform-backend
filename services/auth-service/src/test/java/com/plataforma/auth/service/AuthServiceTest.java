package com.plataforma.auth.service;

import com.plataforma.shared.client.UserServiceClient;
import com.plataforma.shared.client.dto.GoogleUserRequest;
import com.plataforma.shared.client.dto.UserAuthDTO;
import com.plataforma.shared.exception.UnauthorizedAccessException;
import com.plataforma.shared.security.JwtUtils;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class AuthServiceTest {

    @Mock private UserServiceClient userServiceClient;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private GoogleTokenService googleTokenService;

    @InjectMocks
    private AuthService authService;

    private UserAuthDTO activeUser() {
        return UserAuthDTO.builder()
                .id(1L)
                .email("user@test.com")
                .password("hashed")
                .roleName("BASIC")
                .permissions(List.of())
                .active(true)
                .profileCompleted(true)
                .build();
    }

    @Test
    void shouldLoginSuccessfully() {
        UserAuthDTO user = activeUser();
        when(userServiceClient.findByEmail("user@test.com")).thenReturn(user);
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtUtils.generateToken(user)).thenReturn("jwt-token");

        String token = authService.login("user@test.com", "secret");

        assertEquals("jwt-token", token);
        verify(jwtUtils, times(1)).generateToken(user);
    }

    @Test
    void shouldThrowWhenPasswordIsWrong() {
        when(userServiceClient.findByEmail("user@test.com")).thenReturn(activeUser());
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(UnauthorizedAccessException.class,
                () -> authService.login("user@test.com", "wrong"));
        verify(jwtUtils, never()).generateToken(any());
    }

    @Test
    void shouldThrowWhenLocalLoginHasNoPassword() {
        UserAuthDTO googleUser = activeUser();
        googleUser.setPassword(null);
        when(userServiceClient.findByEmail("user@test.com")).thenReturn(googleUser);

        assertThrows(UnauthorizedAccessException.class,
                () -> authService.login("user@test.com", "secret"));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void shouldLoginWithGoogleAndCreateUserWhenMissing() {
        GoogleTokenPayload payload = GoogleTokenPayload.builder()
                .subject("google-123")
                .email("new@test.com")
                .emailVerified(true)
                .firstName("New")
                .lastName("User")
                .pictureUrl("https://example.com/pic.png")
                .build();
        UserAuthDTO created = activeUser();
        created.setEmail("new@test.com");
        created.setGoogleSubject("google-123");
        when(googleTokenService.verify("id-token")).thenReturn(payload);
        when(userServiceClient.findByEmail("new@test.com"))
                .thenThrow(new UnauthorizedAccessException("Credenciales invalidas."));
        when(userServiceClient.createGoogleUser(any(GoogleUserRequest.class))).thenReturn(created);
        when(jwtUtils.generateToken(created)).thenReturn("jwt-google");

        AuthService.LoginResult result = authService.loginWithGoogle("id-token", "INVESTOR");

        assertEquals("jwt-google", result.getAccessToken());
        verify(userServiceClient).createGoogleUser(any(GoogleUserRequest.class));
    }

    @Test
    void shouldLoginWithGoogleAndLinkExistingUser() {
        GoogleTokenPayload payload = GoogleTokenPayload.builder()
                .subject("google-123")
                .email("user@test.com")
                .emailVerified(true)
                .build();
        UserAuthDTO user = activeUser();
        user.setGoogleSubject(null);
        UserAuthDTO linked = activeUser();
        linked.setGoogleSubject("google-123");
        when(googleTokenService.verify("id-token")).thenReturn(payload);
        when(userServiceClient.findByEmail("user@test.com")).thenReturn(user);
        when(userServiceClient.linkGoogleSubject(eq(1L), any(GoogleUserRequest.class))).thenReturn(linked);
        when(jwtUtils.generateToken(linked)).thenReturn("jwt-linked");

        AuthService.LoginResult result = authService.loginWithGoogle("id-token", null);

        assertEquals("jwt-linked", result.getAccessToken());
        verify(userServiceClient).linkGoogleSubject(eq(1L), any(GoogleUserRequest.class));
    }

    @Test
    void shouldThrowWhenUserIsInactive() {
        UserAuthDTO inactive = activeUser();
        inactive.setActive(false);
        when(userServiceClient.findByEmail("user@test.com")).thenReturn(inactive);

        assertThrows(UnauthorizedAccessException.class,
                () -> authService.login("user@test.com", "secret"));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void shouldChangePasswordSuccessfully() {
        when(userServiceClient.findById(1L)).thenReturn(activeUser());
        when(passwordEncoder.matches("old", "hashed")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("new-hashed");

        assertDoesNotThrow(() -> authService.changePassword(1L, "old", "new"));

        verify(userServiceClient, times(1)).updatePassword(eq(1L), eq("new-hashed"));
    }

    @Test
    void shouldThrowWhenOldPasswordIsWrong() {
        when(userServiceClient.findById(1L)).thenReturn(activeUser());
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(UnauthorizedAccessException.class,
                () -> authService.changePassword(1L, "wrong", "new"));
        verify(userServiceClient, never()).updatePassword(anyLong(), anyString());
    }
}

package com.plataforma.auth.controller;

import com.plataforma.auth.dto.ChangePasswordRequest;
import com.plataforma.auth.dto.EmailVerificationConfirmRequest;
import com.plataforma.auth.dto.EmailVerificationRequest;
import com.plataforma.auth.dto.GoogleAuthRequest;
import com.plataforma.auth.dto.LoginRequest;
import com.plataforma.auth.dto.LoginResponse;
import com.plataforma.auth.dto.RegisterRequest;
import com.plataforma.auth.service.AuthService;
import com.plataforma.auth.service.EmailVerificationService;
import com.plataforma.auth.service.RefreshTokenService;
import com.plataforma.shared.dto.ApiResponse;
import com.plataforma.shared.exception.UnauthorizedAccessException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final int    REFRESH_TTL_SECONDS  = 60 * 60 * 24 * 7; // 7 days
    private static final String COOKIE_PATH          = "/api/auth";

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenService refreshTokenService;

    /** Secure en prod (HTTPS); false por default para dev local sobre HTTP. */
    @org.springframework.beans.factory.annotation.Value("${auth.cookie-secure:false}")
    private boolean cookieSecure;

    // ─────────────────────────────────────────────────────────────
    //  POST /api/auth/login  (public)
    // ─────────────────────────────────────────────────────────────

    /**
     * Authenticates the user and returns:
     *   - body: { "data": { "accessToken": "..." } }
     *   - cookie: HttpOnly refresh_token (7 days)
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response) {

        // Validate credentials and obtain access token + userId in one pass
        AuthService.LoginResult result = authService.loginFull(
                request.getEmail(), request.getPassword());

        return issueLoginResponse(response, result);
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<LoginResponse>> googleLogin(
            @RequestBody GoogleAuthRequest request,
            HttpServletResponse response) {

        AuthService.LoginResult result = authService.loginWithGoogle(
                request.getIdToken(),
                request.getRoleName());

        return issueLoginResponse(response, result);
    }

    @PostMapping("/register/request")
    public ResponseEntity<ApiResponse<Void>> register(
            @RequestBody RegisterRequest request) {
        emailVerificationService.requestRegistration(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Te enviamos un codigo de verificacion para completar el registro.",
                null));
    }

    @PostMapping("/email-verification/request")
    public ResponseEntity<ApiResponse<Void>> requestEmailVerification(
            @RequestBody EmailVerificationRequest request) {
        emailVerificationService.requestVerification(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Si la cuenta existe, enviamos un codigo de verificacion.", null));
    }

    @PostMapping("/email-verification/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmEmailVerification(
            @RequestBody EmailVerificationConfirmRequest request) {
        emailVerificationService.confirmVerification(request.getEmail(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success("Email verificado correctamente.", null));
    }

    @PostMapping("/email-verification/resend")
    public ResponseEntity<ApiResponse<Void>> resendEmailVerification(
            @RequestBody EmailVerificationRequest request) {
        emailVerificationService.resendVerification(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Si la cuenta existe, reenviamos el codigo de verificacion.", null));
    }

    // ─────────────────────────────────────────────────────────────
    //  POST /api/auth/refresh  (public)
    // ─────────────────────────────────────────────────────────────

    /**
     * Issues a new access token using the refresh token cookie.
     * Rotates the refresh token (revokes old one, issues new one).
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        String oldRefreshToken = extractRefreshCookie(request).orElse(null);

        if (oldRefreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Refresh token ausente", 401));
        }

        // Validate token and get userId before revoking
        Long userId = refreshTokenService.validate(oldRefreshToken)
                .orElseThrow(() -> new UnauthorizedAccessException("Refresh token inválido o expirado."));

        // Issue new access token (internally re-validates; safe since token still exists)
        String newAccessToken = authService.refresh(oldRefreshToken);

        // Token rotation: revoke old, issue new
        refreshTokenService.revoke(oldRefreshToken);
        String newRefreshToken = refreshTokenService.generate(userId);
        addRefreshCookie(response, newRefreshToken, REFRESH_TTL_SECONDS);

        LoginResponse loginResponse = LoginResponse.builder().accessToken(newAccessToken).build();
        return ResponseEntity.ok(ApiResponse.success("Token renovado", loginResponse));
    }

    // ─────────────────────────────────────────────────────────────
    //  POST /api/auth/logout  (public)
    // ─────────────────────────────────────────────────────────────

    /**
     * Revokes the refresh token cookie and clears it from the browser.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        extractRefreshCookie(request).ifPresent(authService::logout);

        // Clear cookie
        addRefreshCookie(response, "", 0);

        return ResponseEntity.ok(ApiResponse.success("Sesión cerrada", null));
    }

    // ─────────────────────────────────────────────────────────────
    //  POST /api/auth/change-password  (authenticated via gateway)
    // ─────────────────────────────────────────────────────────────

    /**
     * The gateway already validated the JWT and injected X-User-Id (DD002).
     * This service trusts that header — it does not parse the JWT itself.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Contraseña actualizada", null));
    }

    // ─────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────

    private Optional<String> extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    /**
     * Emite la cookie con ResponseCookie (soporta SameSite y Secure nativos,
     * sin reescribir headers a mano). El flag Secure se controla por
     * configuración: true en prod (HTTPS), false en dev local (ADR-0026).
     */
    private void addRefreshCookie(HttpServletResponse response, String value, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseEntity<ApiResponse<LoginResponse>> issueLoginResponse(
            HttpServletResponse response,
            AuthService.LoginResult result) {
        String refreshToken = refreshTokenService.generate(result.getUserId());
        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken(result.getAccessToken())
                .build();
        addRefreshCookie(response, refreshToken, REFRESH_TTL_SECONDS);
        return ResponseEntity.ok(ApiResponse.success("Login exitoso", loginResponse));
    }
}

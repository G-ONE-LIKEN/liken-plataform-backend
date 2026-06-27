// services/auth-service/src/test/java/com/plataforma/auth/controller/AuthControllerTest.java
package com.plataforma.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.auth.dto.RegisterRequest;
import com.plataforma.auth.service.AuthService;
import com.plataforma.auth.service.EmailVerificationService;
import com.plataforma.auth.service.PasswordResetService;
import com.plataforma.auth.service.RefreshTokenService;
import com.plataforma.shared.exception.GlobalExceptionHandler;

import com.plataforma.auth.service.PasswordResetService;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@Tag("unit")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean EmailVerificationService emailVerificationService;
    @MockBean RefreshTokenService refreshTokenService;
    @MockBean PasswordResetService passwordResetService;

    private RegisterRequest validRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setEmail("fede@test.com");
        r.setPassword("mipass123!");
        r.setRoleName("INVESTOR");
        r.setFirstName("Juan");
        r.setLastName("Perez");
        r.setDni("12345678");
        r.setBirthDate(LocalDate.of(1999, 1, 15));
        r.setPhone("+5491112345678");
        r.setCountry("Argentina");
        r.setProvince("Buenos Aires");
        r.setAddress("Calle Falsa 123");
        r.setTermsAccepted(true);
        return r;
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @Test
    void shouldAcceptValidRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isOk());

        verify(emailVerificationService).requestRegistration(any());
    }

    @Test
    void shouldRejectWhenEmailIsMissing() throws Exception {
        RegisterRequest r = validRequest();
        r.setEmail(null);

        mockMvc.perform(post("/api/auth/register/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(r)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).requestRegistration(any());
    }

    @Test
    void shouldRejectWhenEmailIsInvalid() throws Exception {
        RegisterRequest r = validRequest();
        r.setEmail("no-es-un-email");

        mockMvc.perform(post("/api/auth/register/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(r)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).requestRegistration(any());
    }

    @Test
    void shouldRejectWhenPasswordIsMissing() throws Exception {
        RegisterRequest r = validRequest();
        r.setPassword(null);

        mockMvc.perform(post("/api/auth/register/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(r)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).requestRegistration(any());
    }

    @Test
    void shouldRejectWhenFirstNameIsMissing() throws Exception {
        RegisterRequest r = validRequest();
        r.setFirstName(null);

        mockMvc.perform(post("/api/auth/register/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(r)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).requestRegistration(any());
    }

    @Test
    void shouldRejectWhenRoleNameIsMissing() throws Exception {
        RegisterRequest r = validRequest();
        r.setRoleName(null);

        mockMvc.perform(post("/api/auth/register/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(r)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).requestRegistration(any());
    }

    @Test
    void shouldRejectWhenTermsNotAccepted() throws Exception {
        RegisterRequest r = validRequest();
        r.setTermsAccepted(false);

        mockMvc.perform(post("/api/auth/register/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(r)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).requestRegistration(any());
    }

    @Test
    void shouldRejectWhenOnlyEmailAndPasswordSent() throws Exception {
        String body = """
                {
                  "email": "fede@test.com",
                  "password": "mipass123!"
                }
                """;

        mockMvc.perform(post("/api/auth/register/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).requestRegistration(any());
    }

    @Test
    void shouldRejectWhenBirthDateIsInFuture() throws Exception {
        RegisterRequest r = validRequest();
        r.setBirthDate(LocalDate.now().plusYears(1));

        mockMvc.perform(post("/api/auth/register/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(r)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).requestRegistration(any());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    void shouldRejectLoginWithNoBody() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(authService, never()).loginFull(any(), any());
    }

    @Test
    void shouldRejectLoginWithMissingEmail() throws Exception {
        String body = """
            {
              "password": "mipass123!"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(authService, never()).loginFull(any(), any());
    }

    @Test
    void shouldRejectLoginWithInvalidEmail() throws Exception {
        String body = """
            {
              "email": "no-es-un-email",
              "password": "mipass123!"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(authService, never()).loginFull(any(), any());
    }

    @Test
    void shouldRejectLoginWithMissingPassword() throws Exception {
        String body = """
            {
              "email": "fede@test.com"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(authService, never()).loginFull(any(), any());
    }

    // ── Email verification request ────────────────────────────────────────────

    @Test
    void shouldRejectVerificationRequestWithNoEmail() throws Exception {
        mockMvc.perform(post("/api/auth/email-verification/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).requestVerification(any());
    }

    @Test
    void shouldRejectVerificationRequestWithInvalidEmail() throws Exception {
        String body = """
            {
                "email": "no-es-un-email"
            }
        """;

        mockMvc.perform(post("/api/auth/email-verification/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).requestVerification(any());
    }

    // ── Email verification confirm ────────────────────────────────────────────

    @Test
    void shouldRejectConfirmWithNoBody() throws Exception {
        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).confirmVerification(any(), any());
    }

    @Test
    void shouldRejectConfirmWithInvalidCode() throws Exception {
        String body = """
            {
              "email": "fede@test.com",
              "code": "abc"
            }
            """;

        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).confirmVerification(any(), any());
    }

    @Test
    void shouldRejectConfirmWithCodeTooShort() throws Exception {
        String body = """
            {
              "email": "fede@test.com",
              "code": "123"
            }
            """;

        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).confirmVerification(any(), any());
    }

    // ── Email verification resend ─────────────────────────────────────────────

    @Test
    void shouldRejectResendWithInvalidEmail() throws Exception {
        String body = """
                { "email": "no-es-un-email" }
                """;

        mockMvc.perform(post("/api/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(emailVerificationService, never()).resendVerification(any());
    }

    // ── Change password ───────────────────────────────────────────────────────

    @Test
    void shouldRejectChangePasswordWithNoBody() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "1")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(authService, never()).changePassword(any(), any(), any());
    }

    @Test
    void shouldRejectChangePasswordWithShortNewPassword() throws Exception {
        String body = """
            {
                "oldPassword": "mipass123!",
                "newPassword": "abc"
            }
        """;

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "1")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(authService, never()).changePassword(any(), any(), any());
    }
}
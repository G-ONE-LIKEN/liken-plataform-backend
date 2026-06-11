// service/src/main/java/com/plataforma/auth/service/EmailVerificationService.java
package com.plataforma.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.auth.dto.RegisterRequest;
import com.plataforma.shared.client.NotificationServiceClient;
import com.plataforma.shared.client.UserServiceClient;
import com.plataforma.shared.client.dto.LocalUserRegistrationRequest;
import com.plataforma.shared.client.dto.UserAuthDTO;
import com.plataforma.shared.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String CODE_KEY_PREFIX = "email:verification:code:";
    private static final String ATTEMPTS_KEY_PREFIX = "email:verification:attempts:";
    private static final String COOLDOWN_KEY_PREFIX = "email:verification:cooldown:";
    private static final String PENDING_REGISTRATION_KEY_PREFIX = "email:verification:pending-registration:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final UserServiceClient userServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final ObjectMapper objectMapper;

    @Value("${email-verification.ttl-minutes:10}")
    private long ttlMinutes;

    @Value("${email-verification.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    @Value("${email-verification.max-attempts:5}")
    private int maxAttempts;

    public void requestRegistration(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        UserAuthDTO existingUser = userServiceClient.findByEmailOrNull(normalizedEmail);
        if (existingUser != null) {
            throw new IllegalArgumentException("Ya existe una cuenta registrada con ese email.");
        }

        LocalUserRegistrationRequest pendingRegistration = LocalUserRegistrationRequest.builder()
                .email(normalizedEmail)
                .password(request.getPassword())
                .roleName(request.getRoleName())
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

        storePendingRegistration(normalizedEmail, pendingRegistration);
        issueCode(normalizedEmail, normalizedEmail, pendingRegistration.getFirstName(), true);
    }

    public void requestVerification(String email) {
        String normalizedEmail = normalizeEmail(email);
        LocalUserRegistrationRequest pendingRegistration = getPendingRegistration(normalizedEmail);
        if (pendingRegistration != null) {
            storePendingRegistration(normalizedEmail, pendingRegistration);
            issueCode(normalizedEmail, pendingRegistration.getEmail(), pendingRegistration.getFirstName(), true);
            return;
        }

        UserAuthDTO user = userServiceClient.findByEmailOrNull(normalizedEmail);
        if (user == null || !isLocalUser(user) || user.isEmailVerified()) {
            return;
        }

        issueCode(normalizedEmail, user.getEmail(), user.getFirstName(), true);
    }

    public void resendVerification(String email) {
        requestVerification(email);
    }

    public void confirmVerification(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        requireCode(code);

        String storedCode = redisTemplate.opsForValue().get(codeKey(normalizedEmail));
        if (storedCode == null) {
            throw new IllegalArgumentException("El codigo expiro. Solicita uno nuevo.");
        }

        if (!storedCode.equals(code.trim())) {
            long attempts = incrementAttempts(normalizedEmail);
            if (attempts >= maxAttempts) {
                clearCodeState(normalizedEmail);
                throw new IllegalArgumentException("Superaste los intentos permitidos. Solicita un nuevo codigo.");
            }
            throw new IllegalArgumentException("El codigo ingresado es invalido.");
        }

        LocalUserRegistrationRequest pendingRegistration = getPendingRegistration(normalizedEmail);
        if (pendingRegistration != null) {
            userServiceClient.createLocalVerifiedUser(pendingRegistration);
            clearState(normalizedEmail);
            return;
        }

        UserAuthDTO user = userServiceClient.findByEmailOrNull(normalizedEmail);
        if (user == null || !isLocalUser(user)) {
            throw new IllegalArgumentException("El codigo es invalido o expiro. Solicita uno nuevo.");
        }
        if (!user.isEmailVerified()) {
            userServiceClient.markEmailVerified(user.getId());
        }
        clearState(normalizedEmail);
    }

    private void issueCode(
            String normalizedEmail,
            String toEmail,
            String firstName,
            boolean enforceCooldown) {
        if (enforceCooldown && Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey(normalizedEmail)))) {
            throw new RateLimitException(
                    "Espera " + resendCooldownSeconds + " segundos antes de solicitar otro codigo.");
        }

        Duration ttl = Duration.ofMinutes(ttlMinutes);
        String code = generateCode();
        redisTemplate.opsForValue().set(codeKey(normalizedEmail), code, ttl);
        redisTemplate.delete(attemptsKey(normalizedEmail));
        redisTemplate.opsForValue().set(cooldownKey(normalizedEmail), "1", Duration.ofSeconds(resendCooldownSeconds));

        Map<String, Object> variables = new HashMap<>();
        variables.put("firstName", firstName);
        variables.put("code", code);
        variables.put("expiresInMinutes", ttlMinutes);
        notificationServiceClient.sendTransactionalEmail(
                toEmail,
                "Verifica tu email en LIKEN",
                "email-verification",
                variables);
    }

    private long incrementAttempts(String normalizedEmail) {
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey(normalizedEmail));
        redisTemplate.expire(attemptsKey(normalizedEmail), Duration.ofMinutes(ttlMinutes));
        return attempts != null ? attempts : 0;
    }

    private void storePendingRegistration(String email, LocalUserRegistrationRequest registration) {
        try {
            redisTemplate.opsForValue().set(
                    pendingRegistrationKey(email),
                    objectMapper.writeValueAsString(registration),
                    Duration.ofMinutes(ttlMinutes));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo preparar el registro pendiente.", ex);
        }
    }

    private LocalUserRegistrationRequest getPendingRegistration(String email) {
        String payload = redisTemplate.opsForValue().get(pendingRegistrationKey(email));
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, LocalUserRegistrationRequest.class);
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(pendingRegistrationKey(email));
            throw new IllegalStateException("No se pudo recuperar el registro pendiente.", ex);
        }
    }

    private boolean isLocalUser(UserAuthDTO user) {
        return "LOCAL".equalsIgnoreCase(String.valueOf(user.getAuthProvider()));
    }

    private String codeKey(String email) {
        return CODE_KEY_PREFIX + email;
    }

    private String attemptsKey(String email) {
        return ATTEMPTS_KEY_PREFIX + email;
    }

    private String cooldownKey(String email) {
        return COOLDOWN_KEY_PREFIX + email;
    }

    private String pendingRegistrationKey(String email) {
        return PENDING_REGISTRATION_KEY_PREFIX + email;
    }

    private void clearCodeState(String email) {
        redisTemplate.delete(codeKey(email));
        redisTemplate.delete(attemptsKey(email));
        redisTemplate.delete(cooldownKey(email));
    }

    private void clearState(String email) {
        clearCodeState(email);
        redisTemplate.delete(pendingRegistrationKey(email));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("El email es obligatorio.");
        }
        return email.trim().toLowerCase();
    }

    private void requireCode(String code) {
        if (code == null || !code.trim().matches("\\d{6}")) {
            throw new IllegalArgumentException("Ingresa un codigo de 6 digitos.");
        }
    }

    private String generateCode() {
        int value = RANDOM.nextInt(1_000_000);
        return "%06d".formatted(value);
    }
}

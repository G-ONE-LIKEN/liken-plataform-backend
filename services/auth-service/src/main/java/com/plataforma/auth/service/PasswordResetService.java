package com.plataforma.auth.service;

import com.plataforma.shared.client.NotificationServiceClient;
import com.plataforma.shared.client.UserServiceClient;
import com.plataforma.shared.client.dto.UserAuthDTO;
import com.plataforma.shared.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String CODE_KEY_PREFIX      = "password:reset:code:";
    private static final String ATTEMPTS_KEY_PREFIX  = "password:reset:attempts:";
    private static final String COOLDOWN_KEY_PREFIX  = "password:reset:cooldown:";
    private static final SecureRandom RANDOM         = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final UserServiceClient userServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final PasswordEncoder passwordEncoder;
    
    @Value("${email-verification.ttl-minutes:10}")
    private long ttlMinutes;

    @Value("${email-verification.resend-cooldown-seconds:60}")
    private long cooldownSeconds;

    @Value("${email-verification.max-attempts:5}")
    private int maxAttempts;

    /**
     * Solicita el restablecimiento de contraseña.
     * Si el email no existe o no es cuenta LOCAL, responde igual (no revelar si existe).
     */
    public void requestReset(String email) {
        String normalized = normalize(email);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey(normalized)))) {
            throw new RateLimitException("Espera " + cooldownSeconds + " segundos antes de solicitar otro codigo.");
        }

        UserAuthDTO user = userServiceClient.findByEmailOrNull(normalized);

        // Si no existe o es cuenta Google, respondemos igual para no exponer información
        if (user == null || !"LOCAL".equalsIgnoreCase(String.valueOf(user.getAuthProvider()))) {
            return;
        }

        String code = generateCode();
        Duration ttl = Duration.ofMinutes(ttlMinutes);

        redisTemplate.opsForValue().set(codeKey(normalized), code, ttl);
        redisTemplate.delete(attemptsKey(normalized));
        redisTemplate.opsForValue().set(cooldownKey(normalized), "1", Duration.ofSeconds(cooldownSeconds));

        Map<String, Object> variables = new HashMap<>();
        variables.put("firstName", user.getFirstName());
        variables.put("code", code);
        variables.put("expiresInMinutes", ttlMinutes);

        notificationServiceClient.sendTransactionalEmail(
                normalized,
                "Restablece tu contrasena en LIKEN",
                "password-reset",
                variables);
    }

    /**
     * Confirma el código y actualiza la contraseña.
     */
    public void confirmReset(String email, String code, String newPassword) {
        String normalized = normalize(email);

        String storedCode = redisTemplate.opsForValue().get(codeKey(normalized));
        if (storedCode == null) {
            throw new IllegalArgumentException("El codigo expiro. Solicita uno nuevo.");
        }

        if (!storedCode.equals(code.trim())) {
            long attempts = increment(normalized);
            if (attempts >= maxAttempts) {
                clearState(normalized);
                throw new IllegalArgumentException("Superaste los intentos permitidos. Solicita un nuevo codigo.");
            }
            throw new IllegalArgumentException("El codigo ingresado es invalido.");
        }

        UserAuthDTO user = userServiceClient.findByEmailOrNull(normalized);
        if (user == null) {
            throw new IllegalArgumentException("No se encontro una cuenta con ese email.");
        }

        validatePassword(newPassword);/* */

        userServiceClient.updatePassword(user.getId(), passwordEncoder.encode(newPassword));
        clearState(normalized);
    }

    private long increment(String email) {
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey(email));
        redisTemplate.expire(attemptsKey(email), Duration.ofMinutes(ttlMinutes));
        return attempts != null ? attempts : 0;
    }

    private void clearState(String email) {
        redisTemplate.delete(codeKey(email));
        redisTemplate.delete(attemptsKey(email));
        redisTemplate.delete(cooldownKey(email));
    }

    private String codeKey(String email)     { return CODE_KEY_PREFIX + email; }
    private String attemptsKey(String email) { return ATTEMPTS_KEY_PREFIX + email; }
    private String cooldownKey(String email) { return COOLDOWN_KEY_PREFIX + email; }

    private String normalize(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("El email es obligatorio.");
        return email.trim().toLowerCase();
    }

    private String generateCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException(
                "La contrasena debe tener al menos 6 caracteres, una letra, un numero y un caracter especial.");
        }
        boolean hasLetter  = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit   = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        if (!hasLetter || !hasDigit || !hasSpecial) {
            throw new IllegalArgumentException(
                "La contrasena debe tener al menos 6 caracteres, una letra, un numero y un caracter especial.");
        }
    }



}
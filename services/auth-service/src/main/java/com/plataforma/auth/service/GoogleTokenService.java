package com.plataforma.auth.service;

import com.plataforma.shared.exception.UnauthorizedAccessException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class GoogleTokenService {

    private final RestTemplate restTemplate;

    @Value("${google.client-id:}")
    private String googleClientId;

    public GoogleTokenPayload verify(String idToken) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new UnauthorizedAccessException("Google OAuth no esta configurado.");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new UnauthorizedAccessException("Token de Google ausente.");
        }
        try {
            String encoded = UriUtils.encodeQueryParam(idToken, StandardCharsets.UTF_8);
            GoogleTokenInfo tokenInfo = restTemplate.getForObject(
                    "https://oauth2.googleapis.com/tokeninfo?id_token=" + encoded,
                    GoogleTokenInfo.class);
            if (tokenInfo == null || tokenInfo.getSub() == null) {
                throw new UnauthorizedAccessException("Token de Google invalido.");
            }
            if (!googleClientId.equals(tokenInfo.getAud())) {
                throw new UnauthorizedAccessException("Token de Google emitido para otro cliente.");
            }
            if (!"true".equalsIgnoreCase(tokenInfo.getEmail_verified())) {
                throw new UnauthorizedAccessException("Google no confirmo el email de la cuenta.");
            }
            return GoogleTokenPayload.builder()
                    .subject(tokenInfo.getSub())
                    .email(tokenInfo.getEmail())
                    .emailVerified(true)
                    .firstName(tokenInfo.getGiven_name())
                    .lastName(tokenInfo.getFamily_name())
                    .pictureUrl(tokenInfo.getPicture())
                    .build();
        } catch (RestClientException ex) {
            throw new UnauthorizedAccessException("Token de Google invalido o expirado.");
        }
    }

    @Data
    private static class GoogleTokenInfo {
        private String aud;
        private String sub;
        private String email;
        private String email_verified;
        private String given_name;
        private String family_name;
        private String picture;
    }
}

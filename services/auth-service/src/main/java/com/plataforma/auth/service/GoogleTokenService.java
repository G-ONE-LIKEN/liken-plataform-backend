package com.plataforma.auth.service;

import com.plataforma.shared.exception.UnauthorizedAccessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import jakarta.annotation.PostConstruct;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleTokenService {

    @Value("${google.client-id:}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    public void init() {
        if (googleClientId != null && !googleClientId.isBlank()) {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    // El Builder de GoogleIdTokenVerifier por defecto ya valida los issuers correctos:
                    // accounts.google.com y https://accounts.google.com
                    .build();
        }
    }

    public GoogleTokenPayload verify(String idTokenString) {
        if (googleClientId == null || googleClientId.isBlank() || this.verifier == null) {
            throw new UnauthorizedAccessException("Google OAuth no esta configurado.");
        }
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new UnauthorizedAccessException("Token de Google ausente.");
        }

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                // verify retorna null si la firma, exp, iss o aud son invalidos
                throw new UnauthorizedAccessException("Token de Google invalido.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new UnauthorizedAccessException("Google no confirmo el email de la cuenta.");
            }

            return GoogleTokenPayload.builder()
                    .subject(payload.getSubject())
                    .email(payload.getEmail())
                    .emailVerified(true)
                    .firstName((String) payload.get("given_name"))
                    .lastName((String) payload.get("family_name"))
                    .pictureUrl((String) payload.get("picture"))
                    .build();
        } catch (Exception ex) {
            log.error("Error validando token de Google", ex);
            throw new UnauthorizedAccessException("Token de Google invalido o expirado.");
        }
    }
}

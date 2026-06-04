package com.plataforma.shared.client;

import com.plataforma.shared.client.dto.GoogleUserRequest;
import com.plataforma.shared.client.dto.LocalUserRegistrationRequest;
import com.plataforma.shared.client.dto.UserAuthDTO;
import com.plataforma.shared.exception.UnauthorizedAccessException;
import com.plataforma.shared.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.user-service.url}")
    private String userServiceUrl;

    public UserAuthDTO findByEmail(String email) {
        UserAuthDTO user = findByEmailOrNull(email);
        if (user == null) {
            throw new UnauthorizedAccessException("Correo o contrasena incorrectos.");
        }
        return user;
    }

    public UserAuthDTO findByEmailOrNull(String email) {
        try {
            String encodedEmail = UriUtils.encodePathSegment(email, StandardCharsets.UTF_8);
            return restTemplate.getForObject(
                    userServiceUrl + "/internal/users/by-email/" + encodedEmail,
                    UserAuthDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    public UserAuthDTO findById(Long id) {
        try {
            return restTemplate.getForObject(
                    userServiceUrl + "/internal/users/" + id,
                    UserAuthDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new UserNotFoundException(id);
        }
    }

    public void updatePassword(Long id, String encodedPassword) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> entity = new HttpEntity<>(encodedPassword, headers);
        try {
            restTemplate.put(userServiceUrl + "/internal/users/" + id + "/password", entity);
        } catch (HttpClientErrorException.NotFound e) {
            throw new UserNotFoundException(id);
        }
    }

    public UserAuthDTO markEmailVerified(Long id) {
        try {
            return restTemplate.exchange(
                    userServiceUrl + "/internal/users/" + id + "/email-verified",
                    HttpMethod.PUT,
                    null,
                    UserAuthDTO.class).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new UserNotFoundException(id);
        }
    }

    public UserAuthDTO createGoogleUser(GoogleUserRequest request) {
        try {
            return restTemplate.postForObject(
                    userServiceUrl + "/internal/users/google",
                    request,
                    UserAuthDTO.class);
        } catch (HttpClientErrorException e) {
            throw new UnauthorizedAccessException("No se pudo crear la cuenta con Google.");
        }
    }

    public UserAuthDTO createLocalVerifiedUser(LocalUserRegistrationRequest request) {
        try {
            return restTemplate.postForObject(
                    userServiceUrl + "/internal/users/local",
                    request,
                    UserAuthDTO.class);
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("No se pudo completar el registro. Intenta nuevamente.");
        }
    }

    public UserAuthDTO linkGoogleSubject(Long id, GoogleUserRequest request) {
        HttpEntity<GoogleUserRequest> entity = new HttpEntity<>(request);
        try {
            return restTemplate.exchange(
                    userServiceUrl + "/internal/users/" + id + "/google",
                    HttpMethod.PUT,
                    entity,
                    UserAuthDTO.class).getBody();
        } catch (HttpClientErrorException e) {
            throw new UnauthorizedAccessException("No se pudo vincular la cuenta de Google.");
        }
    }
}

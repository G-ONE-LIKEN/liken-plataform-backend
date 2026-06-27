// services/user-service/src/main/java/com/plataforma/user/service/UserService.java
package com.plataforma.user.service;

import com.plataforma.rbac.constant.RoleConstants;
import com.plataforma.rbac.model.Role;
import com.plataforma.rbac.repository.RoleRepository;
import com.plataforma.shared.exception.RoleNotFoundException;
import com.plataforma.shared.exception.UserNotFoundException;
import com.plataforma.user.dto.ProfileRequest;
import com.plataforma.user.event.producer.UserContextEventProducer;
import com.plataforma.user.model.AuthProvider;
import com.plataforma.user.model.DeveloperStatus;
import com.plataforma.user.model.User;
import com.plataforma.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String PASSWORD_COMPLEXITY_MESSAGE =
            "La contrasena debe tener al menos 6 caracteres, una letra, un numero y un caracter especial.";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserContextEventProducer contextEventProducer;
    private final com.plataforma.user.event.producer.DeveloperEventProducer developerEventProducer;
    private final com.plataforma.user.event.producer.UserRegisteredEventProducer userRegisteredEventProducer;

    @Transactional
    public User registerUser(User user, String roleName) {
        return registerLocalUser(user, roleName, false, false);
    }

    @Transactional
    public User registerVerifiedLocalUser(User user, String roleName) {
        return registerLocalUser(user, roleName, true, false);
    }

    /**
     * Variante para registros que llegan desde auth-service con la contraseña
     * YA hasheada (BCrypt). auth-service valida la fortaleza sobre el texto
     * plano y hashea antes de persistir el registro pendiente en Redis
     * (ADR-0026) — acá no se re-valida ni se re-hashea.
     */
    @Transactional
    public User registerVerifiedLocalUser(User user, String roleName, boolean passwordEncoded) {
        return registerLocalUser(user, roleName, true, passwordEncoded);
    }

    private User registerLocalUser(User user, String roleName, boolean emailVerified, boolean passwordEncoded) {
        user.setAuthProvider(AuthProvider.LOCAL);
        validateLocalRegistration(user, passwordEncoded);
        user.setEmail(normalizeEmail(user.getEmail()));
        userRepository.findByEmail(user.getEmail()).ifPresent(existing -> {
            throw new IllegalArgumentException("Ya existe un usuario registrado con ese email.");
        });
        user.setDni(normalizeDni(user.getDni()));
        if (userRepository.existsByDni(user.getDni())) {
            throw new IllegalArgumentException("Ya existe un usuario registrado con ese DNI.");
        }
        if (!passwordEncoded) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        user.setProfileCompleted(true);
        user.setEmailVerified(emailVerified);
        user.setActive(true);
        assignInitialRole(user, roleName);
        User saved = userRepository.save(user);
        if (emailVerified) {
            userRegisteredEventProducer.publishRegistered(saved);
            if (saved.getRole() != null && RoleConstants.DEVELOPER.equals(saved.getRole().getName())) {
                developerEventProducer.publishRegistered(saved);
            }
        }
        return saved;
    }

    public User registerUser(User user) {
        return registerUser(user, null);
    }

    @Transactional
    public User registerGoogleUser(User user, String roleName) {
        requireText(user.getEmail(), "El email es obligatorio.");
        requireText(user.getGoogleSubject(), "El identificador de Google es obligatorio.");
        user.setEmail(normalizeEmail(user.getEmail()));
        user.setAuthProvider(AuthProvider.GOOGLE);
        user.setPassword(null);
        user.setProfileCompleted(false);
        user.setEmailVerified(true);
        user.setTermsAccepted(false);
        user.setActive(true);
        assignInitialRole(user, roleName != null ? roleName : RoleConstants.INVESTOR);
        User saved = userRepository.save(user);
        userRegisteredEventProducer.publishRegistered(saved);
        if (saved.getRole() != null && RoleConstants.DEVELOPER.equals(saved.getRole().getName())) {
            developerEventProducer.publishRegistered(saved);
        }
        return saved;
    }

    @Transactional
    public User linkGoogleSubject(Long userId, String googleSubject, String pictureUrl) {
        requireText(googleSubject, "El identificador de Google es obligatorio.");
        User user = getUserById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        boolean shouldPublishRegistration = !user.isEmailVerified();
        if (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(googleSubject)) {
            throw new IllegalArgumentException("La cuenta ya esta vinculada a otro usuario de Google.");
        }
        userRepository.findByGoogleSubject(googleSubject)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("La cuenta de Google ya esta vinculada a otro usuario.");
                });
        user.setGoogleSubject(googleSubject);
        if (pictureUrl != null && !pictureUrl.isBlank()) {
            user.setPictureUrl(pictureUrl.trim());
        }
        if (user.getAuthProvider() == null) {
            user.setAuthProvider(AuthProvider.LOCAL);
        }
        user.setEmailVerified(true);
        User saved = userRepository.save(user);
        if (shouldPublishRegistration) {
            userRegisteredEventProducer.publishRegistered(saved);
            if (saved.getRole() != null && RoleConstants.DEVELOPER.equals(saved.getRole().getName())) {
                developerEventProducer.publishRegistered(saved);
            }
        }
        contextEventProducer.invalidateContext(userId);
        return saved;
    }

    @Transactional
    public User markEmailVerified(Long userId) {
        User user = getUserById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (user.isEmailVerified()) {
            return user;
        }
        user.setEmailVerified(true);
        User saved = userRepository.save(user);
        userRegisteredEventProducer.publishRegistered(saved);
        if (saved.getRole() != null && RoleConstants.DEVELOPER.equals(saved.getRole().getName())) {
            developerEventProducer.publishRegistered(saved);
        }
        contextEventProducer.invalidateContext(userId);
        return saved;
    }

    @Transactional
    public User completeProfile(Long userId, ProfileRequest request) {
        User user = getUserById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        applyProfile(user, request);
        user.setProfileCompleted(true);
        User saved = userRepository.save(user);
        contextEventProducer.invalidateContext(userId);
        return saved;
    }

    public User updateDeveloperStatus(Long userId, DeveloperStatus status) {
        User user = getUserById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        user.setDeveloperStatus(status);
        User saved = userRepository.save(user);
        contextEventProducer.invalidateContext(userId);
        developerEventProducer.publishStatusChanged(saved, status);
        return saved;
    }

    @Transactional
    public User updateUser(Long id, User details) {
        User user = getUserById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (details.getEmail() != null) user.setEmail(normalizeEmail(details.getEmail()));
        if (details.getFirstName() != null) user.setFirstName(clean(details.getFirstName()));
        if (details.getLastName() != null) user.setLastName(clean(details.getLastName()));
        if (details.getBirthDate() != null) user.setBirthDate(details.getBirthDate());
        if (details.getPhone() != null) user.setPhone(clean(details.getPhone()));
        if (details.getCountry() != null) user.setCountry(clean(details.getCountry()));
        if (details.getProvince() != null) user.setProvince(clean(details.getProvince()));
        if (details.getAddress() != null) user.setAddress(clean(details.getAddress()));
        if (details.isTermsAccepted()) user.setTermsAccepted(true);
        if (details.getDni() != null) {
            String normalizedDni = normalizeDni(details.getDni());
            if (userRepository.existsByDniAndIdNot(normalizedDni, id)) {
                throw new IllegalArgumentException("Ya existe un usuario registrado con ese DNI.");
            }
            user.setDni(normalizedDni);
        }
        return userRepository.save(user);
    }

    public void deactivateUser(Long id) {
        User user = getUserById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        user.setActive(false);
        userRepository.save(user);
        contextEventProducer.invalidateContext(id);
    }

    public void activateUser(Long id) {
        User user = getUserById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        user.setActive(true);
        userRepository.save(user);
        contextEventProducer.invalidateContext(id);
    }

    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public Page<User> getAllDevelopers(Pageable pageable) {
        return userRepository.findByRole_Name(RoleConstants.DEVELOPER, pageable);
    }

    public Page<User> getDevelopersByStatus(DeveloperStatus status, Pageable pageable) {
        return userRepository.findByRole_NameAndDeveloperStatus(RoleConstants.DEVELOPER, status, pageable);
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email));
    }

    public Optional<Role> getRoleById(Long id) {
        return roleRepository.findById(id);
    }

    public User assignRole(User user, Role role) {
        user.setRole(role);
        User saved = userRepository.save(user);
        contextEventProducer.invalidateContext(user.getId());
        return saved;
    }

    @Transactional
    public void updatePassword(Long id, String encodedPassword) {
        User user = getUserById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        user.setPassword(encodedPassword);
        userRepository.save(user);
    }

    private void assignInitialRole(User user, String roleName) {
        String resolvedRole = (roleName != null &&
                (RoleConstants.INVESTOR.equals(roleName) || RoleConstants.DEVELOPER.equals(roleName)))
                ? roleName : RoleConstants.BASIC;
        Role role = roleRepository.findByName(resolvedRole)
                .orElseThrow(() -> new RoleNotFoundException("Rol no encontrado: " + resolvedRole));
        user.setRole(role);
        if (RoleConstants.DEVELOPER.equals(resolvedRole)) {
            user.setDeveloperStatus(DeveloperStatus.PENDING);
        }
    }

    private void validateLocalRegistration(User user, boolean passwordEncoded) {
        requireText(user.getEmail(), "El email es obligatorio.");
        requireText(user.getPassword(), "La contrasena es obligatoria.");
        if (!passwordEncoded) {
            // Con passwordEncoded la fortaleza ya se validó en auth-service
            // sobre el texto plano; un hash BCrypt no es validable acá.
            validatePasswordStrength(user.getPassword());
        }
        validateProfileFields(user.getFirstName(), user.getLastName(), user.getDni(), user.getBirthDate(),
                user.getPhone(), user.getCountry(), user.getProvince(), user.getAddress(), user.isTermsAccepted());
    }

    private void applyProfile(User user, ProfileRequest request) {
        validateProfileFields(request.getFirstName(), request.getLastName(), request.getDni(), request.getBirthDate(),
                request.getPhone(), request.getCountry(), request.getProvince(), request.getAddress(),
                request.isTermsAccepted());
        String normalizedDni = normalizeDni(request.getDni());
        if (userRepository.existsByDniAndIdNot(normalizedDni, user.getId())) {
            throw new IllegalArgumentException("Ya existe un usuario registrado con ese DNI.");
        }
        user.setFirstName(clean(request.getFirstName()));
        user.setLastName(clean(request.getLastName()));
        user.setDni(normalizedDni);
        user.setBirthDate(request.getBirthDate());
        user.setPhone(clean(request.getPhone()));
        user.setCountry(clean(request.getCountry()));
        user.setProvince(clean(request.getProvince()));
        user.setAddress(clean(request.getAddress()));
        user.setTermsAccepted(true);
    }

    private void validateProfileFields(String firstName, String lastName, String dni, LocalDate birthDate,
            String phone, String country, String province, String address, boolean termsAccepted) {
        requireText(firstName, "El nombre es obligatorio.");
        requireText(lastName, "El apellido es obligatorio.");
        requireText(dni, "El DNI es obligatorio.");
        requireText(phone, "El telefono es obligatorio.");
        requireText(country, "El pais es obligatorio.");
        requireText(province, "La provincia es obligatoria.");
        requireText(address, "La direccion es obligatoria.");
        if (birthDate == null) {
            throw new IllegalArgumentException("La fecha de nacimiento es obligatoria.");
        }
        if (birthDate.isAfter(LocalDate.now().minusYears(18))) {
            throw new IllegalArgumentException("Debes ser mayor de 18 anos para registrarte.");
        }
        normalizeDni(dni);
        if (!termsAccepted) {
            throw new IllegalArgumentException("Debes aceptar los terminos y condiciones.");
        }
    }

    private String normalizeDni(String dni) {
        String normalized = dni == null ? "" : dni.replaceAll("[^0-9]", "");
        if (normalized.length() < 7 || normalized.length() > 9) {
            throw new IllegalArgumentException("El DNI debe tener entre 7 y 9 digitos.");
        }
        return normalized;
    }

    private String normalizeEmail(String email) {
        requireText(email, "El email es obligatorio.");
        return email.trim().toLowerCase();
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validatePasswordStrength(String password) {
        if (password.length() < 6) {
            throw new IllegalArgumentException(PASSWORD_COMPLEXITY_MESSAGE);
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        if (!hasLetter || !hasDigit || !hasSpecial) {
            throw new IllegalArgumentException(PASSWORD_COMPLEXITY_MESSAGE);
        }
    }
}

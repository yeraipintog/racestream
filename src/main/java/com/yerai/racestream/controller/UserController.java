/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.4
 * @created 05-05-2026
 * @modified 07-05-2026
 * @description API privada de cuenta, preferencias de avisos y privacidad
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.repository.AppUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private static final String PASSWORD_REQUIREMENTS_MESSAGE = "La contraseña no cumple los requisitos";
    private static final String PASSWORD_MISMATCH_MESSAGE = "Las contraseñas no coinciden";

    public UserController(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 05-05-2026
     * @modified 07-05-2026
     * @description Actualiza nombre de usuario y preferencias de avisos del usuario
     * @param request Datos editables
     * @param authentication Sesion actual
     * @return Usuario actualizado
     */
    @PutMapping("/profile")
    public ResponseEntity<?> profile(@RequestBody ProfileRequest request, Authentication authentication) {
        AppUser user = currentUser(authentication);
        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            if (!name.equalsIgnoreCase(user.getName()) && appUserRepository.existsByNameIgnoreCase(name)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Ya existe una cuenta con ese nombre de usuario"));
            }
            user.setName(name);
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (!email.equalsIgnoreCase(user.getEmail()) && appUserRepository.existsByEmailIgnoreCase(email)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Ya existe una cuenta con ese email"));
            }
            user.setEmail(email);
        }
        if (request.notificationsEnabled() != null) user.setNotificationsEnabled(request.notificationsEnabled());
        if (request.emailNotificationsEnabled() != null) user.setEmailNotificationsEnabled(request.emailNotificationsEnabled());
        if (request.favoriteDigestEnabled() != null) user.setFavoriteDigestEnabled(request.favoriteDigestEnabled());
        if (request.favoriteDigestEmailEnabled() != null) user.setFavoriteDigestEmailEnabled(request.favoriteDigestEmailEnabled());
        if (request.privateProfile() != null) user.setPrivateProfile(request.privateProfile());
        appUserRepository.save(user);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 05-05-2026
     * @modified 12-05-2026
     * @description Cambia la contrasena de una cuenta local
     * @param request Contrasena nueva
     * @param authentication Sesion actual
     * @return Estado de guardado
     */
    @PutMapping("/password")
    public ResponseEntity<?> password(@RequestBody PasswordRequest request, Authentication authentication) {
        if (request.confirmPassword() != null && !Objects.equals(request.password(), request.confirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", PASSWORD_MISMATCH_MESSAGE));
        }
        if (!isStrongPassword(request.password())) {
            return ResponseEntity.badRequest().body(Map.of("error", PASSWORD_REQUIREMENTS_MESSAGE));
        }
        AppUser user = currentUser(authentication);
        user.setPassword(passwordEncoder.encode(request.password()));
        appUserRepository.save(user);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    private AppUser currentUser(Authentication authentication) {
        return appUserRepository.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    }

    private boolean isStrongPassword(String password) {
        return password != null
                && password.length() >= 8
                && password.matches(".*[a-z].*")
                && password.matches(".*[A-Z].*")
                && password.matches(".*\\d.*")
                && password.matches(".*[^A-Za-z0-9].*");
    }

    public record ProfileRequest(String name, String email, Boolean notificationsEnabled, Boolean emailNotificationsEnabled,
            Boolean favoriteDigestEnabled, Boolean favoriteDigestEmailEnabled, Boolean privateProfile) {}
    public record PasswordRequest(String password, String confirmPassword) {}
}

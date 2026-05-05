/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 05-05-2026
 * @modified 05-05-2026
 * @description API privada de cuenta, preferencias y privacidad
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

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Actualiza nombre y preferencias de usuario
     * @param request Datos editables
     * @param authentication Sesion actual
     * @return Usuario actualizado
     */
    @PutMapping("/profile")
    public ResponseEntity<?> profile(@RequestBody ProfileRequest request, Authentication authentication) {
        AppUser user = currentUser(authentication);
        if (request.name() != null && !request.name().isBlank()) user.setName(request.name().trim());
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (!email.equalsIgnoreCase(user.getEmail()) && appUserRepository.existsByEmailIgnoreCase(email)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Ya existe una cuenta con ese email"));
            }
            user.setEmail(email);
        }
        if (request.notificationsEnabled() != null) user.setNotificationsEnabled(request.notificationsEnabled());
        if (request.privateProfile() != null) user.setPrivateProfile(request.privateProfile());
        appUserRepository.save(user);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Cambia la contrasena de una cuenta local
     * @param request Contrasena nueva
     * @param authentication Sesion actual
     * @return Estado de guardado
     */
    @PutMapping("/password")
    public ResponseEntity<?> password(@RequestBody PasswordRequest request, Authentication authentication) {
        if (!isStrongPassword(request.password())) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña debe tener 8 caracteres, mayúscula, minúscula, número y símbolo"));
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

    public record ProfileRequest(String name, String email, Boolean notificationsEnabled, Boolean privateProfile) {}
    public record PasswordRequest(String password) {}
}

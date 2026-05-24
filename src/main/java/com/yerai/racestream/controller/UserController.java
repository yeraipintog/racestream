/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.2
 * @created 05-05-2026
 * @modified 22-05-2026
 * @description API privada de cuenta, preferencias, borrado propio y privacidad
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.UserRole;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.service.UserAccountDeletionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final UserAccountDeletionService userAccountDeletionService;
    private static final String PASSWORD_REQUIREMENTS_MESSAGE = "La contraseña no cumple los requisitos";
    private static final String PASSWORD_MISMATCH_MESSAGE = "Las contraseñas no coinciden";

    private static final String CURRENT_PASSWORD_REQUIRED_MESSAGE = "La contraseña actual es obligatoria";
    private static final String CURRENT_PASSWORD_INVALID_MESSAGE = "La contraseña actual no es correcta";
    private static final String SAME_PASSWORD_MESSAGE = "Ya es tu contraseña actual";
    private static final String ADMIN_ACCOUNT_LOCKED_MESSAGE = "La cuenta ADMIN no permite cambios de credenciales";

    @Autowired
    public UserController(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            UserAccountDeletionService userAccountDeletionService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.userAccountDeletionService = userAccountDeletionService;
    }

    UserController(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this(appUserRepository, passwordEncoder, null);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 05-05-2026
     * @modified 07-05-2026
     * @description Actualiza nombre de usuario y preferencias de avisos del usuario
     * @param request Datos editables
     * @param authentication Sesión actual
     * @return Usuario actualizado
     */
    @PutMapping("/profile")
    public ResponseEntity<?> profile(
            @RequestBody ProfileRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AppUser user = currentUser(authentication);
        if (user.getRole() == UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", ADMIN_ACCOUNT_LOCKED_MESSAGE));
        }
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
        syncSecurityContext(user, authentication, servletRequest);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 05-05-2026
     * @modified 22-05-2026
     * @description Cambia la contrasena de una cuenta local evitando repetir la actual
     * @param request Contrasena nueva
     * @param authentication Sesión actual
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
        if (user.getRole() == UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", ADMIN_ACCOUNT_LOCKED_MESSAGE));
        }
        if (isBlank(request.currentPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", CURRENT_PASSWORD_REQUIRED_MESSAGE));
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            return ResponseEntity.status(403).body(Map.of("error", CURRENT_PASSWORD_INVALID_MESSAGE));
        }
        if (passwordEncoder.matches(request.password(), user.getPassword())) {
            return fieldError(400, "password", SAME_PASSWORD_MESSAGE);
        }
        user.setPassword(passwordEncoder.encode(request.password()));
        appUserRepository.save(user);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 19-05-2026
     * @modified 19-05-2026
     * @description Elimina la cuenta actual si no es ADMIN e invalida la sesion activa
     * @param authentication Sesión actual
     * @param request Peticion HTTP actual
     * @param response Respuesta HTTP para limpiar cookie de sesion
     * @return Estado de borrado
     */
    @DeleteMapping("/account")
    @Transactional
    public ResponseEntity<?> deleteAccount(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        AppUser user = currentUser(authentication);
        if (user.getRole() == UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Las cuentas ADMIN no se pueden eliminar"));
        }
        if (userAccountDeletionService != null) {
            userAccountDeletionService.deleteUserData(user);
        }
        appUserRepository.delete(user);
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        ResponseCookie cookie = ResponseCookie.from("JSESSIONID", "")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private AppUser currentUser(Authentication authentication) {
        return appUserRepository.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 19-05-2026
     * @modified 19-05-2026
     * @description Actualiza el principal de Spring Security para que un cambio de email no rompa la sesion
     * @param user Usuario persistido
     * @param authentication Autenticacion previa
     * @param request Peticion HTTP actual
     */
    private void syncSecurityContext(AppUser user, Authentication authentication, HttpServletRequest request) {
        if (authentication == null || request == null) {
            return;
        }
        UserDetails principal = User.withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
        UsernamePasswordAuthenticationToken updatedAuthentication = new UsernamePasswordAuthenticationToken(
                principal,
                authentication.getCredentials(),
                principal.getAuthorities());
        updatedAuthentication.setDetails(authentication.getDetails());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(updatedAuthentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private boolean isStrongPassword(String password) {
        return password != null
                && password.length() >= 8
                && password.matches(".*[a-z].*")
                && password.matches(".*[A-Z].*")
                && password.matches(".*\\d.*")
                && password.matches(".*[^A-Za-z0-9].*");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @modified 22-05-2026
     * @description Construye errores JSON asociados a un campo de formulario privado
     * @param status Estado HTTP
     * @param field Campo afectado
     * @param error Mensaje visible
     * @return Respuesta JSON
     */
    private ResponseEntity<?> fieldError(int status, String field, String error) {
        return ResponseEntity.status(status).body(Map.of("field", field, "error", error));
    }

    public record ProfileRequest(String name, String email, Boolean notificationsEnabled, Boolean emailNotificationsEnabled,
            Boolean favoriteDigestEnabled, Boolean favoriteDigestEmailEnabled, Boolean privateProfile) {}
    public record PasswordRequest(String password, String confirmPassword, String currentPassword) {
        public PasswordRequest(String password, String confirmPassword) {
            this(password, confirmPassword, null);
        }
    }
}

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 05-05-2026
 * @modified 05-05-2026
 * @description API de registro, login, sesion y cookies de RaceStream
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.AuthProvider;
import com.yerai.racestream.model.UserRole;
import com.yerai.racestream.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthController(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Registra un usuario local con password cifrada y aceptacion legal obligatoria
     * @param request Datos del formulario de registro
     * @param servletRequest Peticion HTTP para abrir sesion
     * @return Usuario registrado sin exponer la contrasena
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        if (isBlank(request.name()) || isBlank(request.email()) || isBlank(request.password())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nombre, email y contraseña son obligatorios"));
        }
        if (!isStrongPassword(request.password())) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña debe tener 8 caracteres, mayúscula, minúscula, número y símbolo"));
        }
        if (!Boolean.TRUE.equals(request.acceptPolicies())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debes aceptar las políticas para registrarte"));
        }
        if (appUserRepository.existsByEmailIgnoreCase(request.email())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ya existe una cuenta con ese email"));
        }

        AppUser user = new AppUser();
        user.setName(request.name().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setProvider(AuthProvider.LOCAL);
        user.setPoliciesAccepted(true);
        user.setCookieConsent(Boolean.TRUE.equals(request.cookieConsent()));
        appUserRepository.save(user);
        authenticate(user.getEmail(), request.password(), servletRequest);
        return ResponseEntity.ok(toResponse(user));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Inicia sesion por email y contrasena usando Spring Security
     * @param request Credenciales
     * @param servletRequest Peticion HTTP para almacenar la sesion
     * @return Usuario autenticado
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        Authentication authentication = authenticate(request.email(), request.password(), servletRequest);
        AppUser user = appUserRepository.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(toResponse(user));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Devuelve el estado de sesion actual
     * @param principal Principal autenticado local
     * @return Datos publicos de sesion
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal Object principal) {
        return currentUser(principal)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(toResponse(user)))
                .orElseGet(() -> ResponseEntity.ok(Map.of("authenticated", false)));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Guarda consentimiento de cookies en cookie tecnica y, si hay usuario, en BBDD
     * @param request Preferencia de cookies
     * @param principal Usuario opcional
     * @param response Respuesta HTTP
     * @return Estado actualizado
     */
    @PostMapping("/cookies")
    public ResponseEntity<?> cookies(
            @RequestBody CookieRequest request,
            @AuthenticationPrincipal Object principal,
            HttpServletResponse response) {
        boolean accepted = Boolean.TRUE.equals(request.accepted());
        currentUser(principal).ifPresent(user -> {
            user.setCookieConsent(accepted);
            appUserRepository.save(user);
        });
        ResponseCookie cookie = ResponseCookie.from("rs_cookie_consent", accepted ? "accepted" : "rejected")
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofDays(180))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("accepted", accepted));
    }

    private Authentication authenticate(String email, String password, HttpServletRequest request) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return authentication;
    }

    private java.util.Optional<AppUser> currentUser(Object principal) {
        String email = null;
        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else if (principal instanceof String value) {
            email = value;
        }
        if (email == null || "anonymousUser".equals(email)) {
            return java.util.Optional.empty();
        }
        return appUserRepository.findByEmailIgnoreCase(email);
    }

    private Map<String, Object> toResponse(AppUser user) {
        return Map.of(
                "authenticated", true,
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "provider", user.getProvider().name(),
                "cookieConsent", user.isCookieConsent(),
                "notificationsEnabled", user.isNotificationsEnabled(),
                "privateProfile", user.isPrivateProfile());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isStrongPassword(String password) {
        return password != null
                && password.length() >= 8
                && password.matches(".*[a-z].*")
                && password.matches(".*[A-Z].*")
                && password.matches(".*\\d.*")
                && password.matches(".*[^A-Za-z0-9].*");
    }

    public record RegisterRequest(String name, String email, String password, Boolean acceptPolicies, Boolean cookieConsent) {}
    public record LoginRequest(String email, String password) {}
    public record CookieRequest(Boolean accepted) {}
}

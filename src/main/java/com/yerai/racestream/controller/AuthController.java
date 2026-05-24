/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.1
 * @created 05-05-2026
 * @modified 22-05-2026
 * @description API de registro, login, recuperacion, sesion, cookies con estado explicito, bloqueo de correos y acceso admin
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.AuthProvider;
import com.yerai.racestream.model.CookieConsentStatus;
import com.yerai.racestream.model.UserRole;
import com.yerai.racestream.config.AdminUserInitializer;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.BlockedEmailRepository;
import com.yerai.racestream.service.PasswordResetMailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String COOKIE_CONSENT_COOKIE_NAME = "rs_cookie_consent";
    private static final String PASSWORD_REQUIREMENTS_MESSAGE = "La contraseña no cumple los requisitos";
    private static final String PASSWORD_MISMATCH_MESSAGE = "Las contraseñas no coinciden";
    private static final String SAME_PASSWORD_MESSAGE = "Ya es tu contraseña actual";

    private final AppUserRepository appUserRepository;
    private final BlockedEmailRepository blockedEmailRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final PasswordResetMailService passwordResetMailService;

    public AuthController(
            AppUserRepository appUserRepository,
            BlockedEmailRepository blockedEmailRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            PasswordResetMailService passwordResetMailService) {
        this.appUserRepository = appUserRepository;
        this.blockedEmailRepository = blockedEmailRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.passwordResetMailService = passwordResetMailService;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.7
     * @created 05-05-2026
     * @modified 18-05-2026
     * @description Registra un usuario local con nombre visible, password cifrada, aceptacion legal, cookies sin decidir y bloqueo administrativo
     * @param request Datos del formulario de registro
     * @param servletRequest Peticion HTTP para abrir sesion
     * @return Usuario registrado sin exponer la contrasena
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        if (isBlank(request.name()) || isBlank(request.email()) || isBlank(request.password())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nombre de usuario, email y contraseña son obligatorios"));
        }
        String username = request.name().trim();
        String email = normalizeEmail(request.email());
        if (blockedEmailRepository.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.status(403).body(Map.of("error", "Este correo electrónico está bloqueado por administración"));
        }
        if (request.confirmPassword() != null && !Objects.equals(request.password(), request.confirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", PASSWORD_MISMATCH_MESSAGE));
        }
        if (!isStrongPassword(request.password())) {
            return ResponseEntity.badRequest().body(Map.of("error", PASSWORD_REQUIREMENTS_MESSAGE));
        }
        if (!Boolean.TRUE.equals(request.acceptPolicies())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debes aceptar las políticas para registrarte"));
        }
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            return fieldError(400, "email", "Correo electrónico ya registrado");
        }
        if (appUserRepository.existsByNameIgnoreCase(username)) {
            return fieldError(400, "name", "Nombre de usuario ya registrado");
        }

        AppUser user = new AppUser();
        user.setName(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setProvider(AuthProvider.LOCAL);
        user.setPoliciesAccepted(true);
        user.setCookieConsentStatus(CookieConsentStatus.UNDECIDED);
        try {
            appUserRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            return resolveDuplicatedUserField(username, email);
        }
        authenticate(user.getEmail(), request.password(), servletRequest);
        return ResponseEntity.ok(toResponse(user));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 05-05-2026
     * @modified 13-05-2026
     * @description Inicia sesion por email, nombre de usuario o alias admin usando Spring Security
     * @param request Credenciales
     * @param servletRequest Peticion HTTP para almacenar la sesion
     * @return Usuario autenticado
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String login = resolveLogin(request.email());
        AppUser candidate = appUserRepository.findByEmailIgnoreCase(normalizeEmail(login))
                .or(() -> appUserRepository.findByNameIgnoreCase(login))
                .orElse(null);
        if (candidate == null) {
            return fieldError(404, "email", "Usuario o email no registrado");
        }
        if (blockedEmailRepository.existsByEmailIgnoreCase(candidate.getEmail())) {
            return fieldError(403, "email", "Cuenta bloqueada por administración");
        }
        try {
            Authentication authentication = authenticate(login, request.password(), servletRequest);
            AppUser user = appUserRepository.findByEmailIgnoreCase(authentication.getName()).orElse(candidate);
            return ResponseEntity.ok(toResponse(user));
        } catch (BadCredentialsException ex) {
            return fieldError(401, "password", "Contraseña incorrecta");
        } catch (DisabledException ex) {
            return fieldError(403, "email", "Cuenta bloqueada por administración");
        } catch (AuthenticationException ex) {
            return fieldError(401, "password", "Contraseña incorrecta");
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 07-05-2026
     * @modified 22-05-2026
     * @description Genera un token temporal y avisa en el campo si el email no existe
     * @param request Email indicado por el usuario
     * @param servletRequest Peticion HTTP para construir la URL publica
     * @return Estado del envio o error asociado al campo email
     */
    @PostMapping("/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
        String email = normalizeEmail(request.email());
        if (isBlank(email)) {
            return fieldError(400, "email", "Email obligatorio");
        }
        boolean blocked = !isBlank(email) && blockedEmailRepository.existsByEmailIgnoreCase(email);
        if (blocked) {
            return ResponseEntity.ok(Map.of("requested", true, "mailSent", false, "blocked", true));
        }
        AppUser user = appUserRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            return fieldError(404, "email", "Email no registrado");
        }
        boolean mailSent = preparePasswordReset(user, servletRequest);
        return ResponseEntity.ok(Map.of("requested", true, "mailSent", mailSent, "blocked", blocked));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @modified 22-05-2026
     * @description Comprueba si un enlace de restablecimiento sigue vigente antes de mostrar el formulario
     * @param token Token recibido por correo
     * @return Estado de validez del enlace
     */
    @GetMapping("/password-reset/validate")
    public ResponseEntity<?> validatePasswordReset(@RequestParam String token) {
        boolean valid = !isBlank(token)
                && appUserRepository.existsByPasswordResetTokenAndPasswordResetExpiresAtAfter(token, Instant.now());
        if (!valid) {
            return ResponseEntity.badRequest().body(Map.of("error", "El enlace de restablecimiento ha caducado o no es válido"));
        }
        return ResponseEntity.ok(Map.of("valid", true));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 07-05-2026
     * @modified 22-05-2026
     * @description Valida el token, evita repetir contrasena, caduca el enlace y abre sesion
     * @param request Token y contrasena nueva
     * @param servletRequest Peticion HTTP para abrir sesion automaticamente
     * @return Estado de guardado
     */
    @PostMapping("/password-reset/confirm")
    @Transactional
    public ResponseEntity<?> confirmPasswordReset(
            @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest servletRequest) {
        if (request.confirmPassword() != null && !Objects.equals(request.password(), request.confirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", PASSWORD_MISMATCH_MESSAGE));
        }
        if (!isStrongPassword(request.password())) {
            return ResponseEntity.badRequest().body(Map.of("error", PASSWORD_REQUIREMENTS_MESSAGE));
        }
        if (isBlank(request.token())) {
            return ResponseEntity.badRequest().body(Map.of("error", "El enlace no es válido"));
        }
        AppUser user = appUserRepository.findByPasswordResetToken(request.token())
                .filter(candidate -> candidate.getPasswordResetExpiresAt() != null
                        && candidate.getPasswordResetExpiresAt().isAfter(Instant.now()))
                .orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El enlace de restablecimiento ha caducado o no es válido"));
        }
        if (passwordEncoder.matches(request.password(), user.getPassword())) {
            return fieldError(400, "password", SAME_PASSWORD_MESSAGE);
        }
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        appUserRepository.save(user);
        if (servletRequest != null) {
            authenticate(user.getEmail(), request.password(), servletRequest);
        }
        return ResponseEntity.ok(Map.of("saved", true));
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
     * @version 1.1.0
     * @created 05-05-2026
     * @modified 18-05-2026
     * @description Guarda decision aceptada o rechazada en cookie tecnica y, si hay usuario, en BBDD
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
        CookieConsentStatus status = resolveCookieConsentStatus(request);
        if (status == CookieConsentStatus.UNDECIDED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debes aceptar o rechazar las cookies"));
        }
        currentUser(principal).ifPresent(user -> {
            user.setCookieConsentStatus(status);
            appUserRepository.save(user);
        });
        ResponseCookie cookie = ResponseCookie.from(COOKIE_CONSENT_COOKIE_NAME, cookieValue(status))
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofDays(180))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of(
                "accepted", status == CookieConsentStatus.ACCEPTED,
                "cookieConsentStatus", status.name()));
    }

    private Authentication authenticate(String email, String password, HttpServletRequest request) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return authentication;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Resuelve duplicados detectados por la base de datos tras una carrera de registro
     * @param username Nombre normalizado
     * @param email Email normalizado
     * @return Error asociado al campo correcto
     */
    private ResponseEntity<?> resolveDuplicatedUserField(String username, String email) {
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            return fieldError(400, "email", "Correo electrónico ya registrado");
        }
        if (appUserRepository.existsByNameIgnoreCase(username)) {
            return fieldError(400, "name", "Nombre de usuario ya registrado");
        }
        return ResponseEntity.badRequest().body(Map.of("error", "No se ha podido crear la cuenta"));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Construye errores JSON asociados a un campo de formulario
     * @param status Estado HTTP
     * @param field Campo afectado
     * @param error Mensaje visible
     * @return Respuesta JSON
     */
    private ResponseEntity<?> fieldError(int status, String field, String error) {
        return ResponseEntity.status(status).body(Map.of("field", field, "error", error));
    }

    private String resolveLogin(String login) {
        String value = login == null ? "" : login.trim();
        return AdminUserInitializer.ADMIN_LOGIN.equalsIgnoreCase(value) ? AdminUserInitializer.ADMIN_EMAIL : value;
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

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @modified 18-05-2026
     * @description Convierte la peticion legacy accepted en un estado explicito de cookies
     * @param request Peticion recibida
     * @return Estado de consentimiento
     */
    private CookieConsentStatus resolveCookieConsentStatus(CookieRequest request) {
        if (request == null || request.accepted() == null) {
            return CookieConsentStatus.UNDECIDED;
        }
        return Boolean.TRUE.equals(request.accepted()) ? CookieConsentStatus.ACCEPTED : CookieConsentStatus.REJECTED;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @modified 18-05-2026
     * @description Traduce el estado persistente al valor de cookie tecnica usado por el navegador
     * @param status Estado persistente
     * @return Valor de cookie
     */
    private String cookieValue(CookieConsentStatus status) {
        return status == CookieConsentStatus.ACCEPTED ? "accepted" : "rejected";
    }

    private Map<String, Object> toResponse(AppUser user) {
        CookieConsentStatus cookieConsentStatus = user.getCookieConsentStatus();
        return Map.ofEntries(
                Map.entry("authenticated", true),
                Map.entry("id", user.getId()),
                Map.entry("name", user.getName()),
                Map.entry("email", user.getEmail()),
                Map.entry("role", user.getRole().name()),
                Map.entry("provider", user.getProvider().name()),
                Map.entry("cookieConsent", cookieConsentStatus == CookieConsentStatus.ACCEPTED),
                Map.entry("cookieConsentStatus", cookieConsentStatus.name()),
                Map.entry("cookieConsentDecided", cookieConsentStatus != CookieConsentStatus.UNDECIDED),
                Map.entry("notificationsEnabled", user.isNotificationsEnabled()),
                Map.entry("emailNotificationsEnabled", user.isEmailNotificationsEnabled()),
                Map.entry("favoriteDigestEnabled", user.isFavoriteDigestEnabled()),
                Map.entry("favoriteDigestEmailEnabled", user.isFavoriteDigestEmailEnabled()),
                Map.entry("privateProfile", user.isPrivateProfile()));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Guarda token temporal y lanza el correo de recuperacion sin romper la respuesta si SMTP falla
     * @param user Usuario que solicita recuperacion
     * @param request Peticion HTTP original
     * @return true si el correo salio
     */
    private boolean preparePasswordReset(AppUser user, HttpServletRequest request) {
        String token = generateResetToken();
        user.setPasswordResetToken(token);
        user.setPasswordResetExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        appUserRepository.save(user);
        try {
            return passwordResetMailService.sendResetLink(user, buildResetUrl(request, token));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Crea un token aleatorio apto para URL
     * @return Token seguro
     */
    private String generateResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Construye la URL que abre el formulario de nueva contrasena
     * @param request Peticion HTTP
     * @param token Token temporal
     * @return URL absoluta de restablecimiento
     */
    private String buildResetUrl(HttpServletRequest request, String token) {
        String proto = firstHeader(request, "X-Forwarded-Proto", request.getScheme());
        String host = firstHeader(request, "X-Forwarded-Host", request.getServerName() + ":" + request.getServerPort());
        return proto + "://" + host + "/login.html?resetToken=" + token;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Lee una cabecera HTTP con fallback
     * @param request Peticion HTTP
     * @param header Cabecera
     * @param fallback Valor alternativo
     * @return Valor elegido
     */
    private String firstHeader(HttpServletRequest request, String header, String fallback) {
        String value = request.getHeader(header);
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private boolean isStrongPassword(String password) {
        return password != null
                && password.length() >= 8
                && password.matches(".*[a-z].*")
                && password.matches(".*[A-Z].*")
                && password.matches(".*\\d.*")
                && password.matches(".*[^A-Za-z0-9].*");
    }

    public record RegisterRequest(String name, String email, String password, String confirmPassword, Boolean acceptPolicies) {}
    public record LoginRequest(String email, String password) {}
    public record PasswordResetRequest(String email) {}
    public record PasswordResetConfirmRequest(String token, String password, String confirmPassword) {}
    public record CookieRequest(Boolean accepted) {}
}

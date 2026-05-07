/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.5
 * @created 05-05-2026
 * @modified 07-05-2026
 * @description API de registro, login, recuperacion, sesion, cookies, bloqueo de correos y acceso admin de RaceStream
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.AuthProvider;
import com.yerai.racestream.model.UserRole;
import com.yerai.racestream.config.AdminUserInitializer;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.BlockedEmailRepository;
import com.yerai.racestream.service.PasswordResetMailService;
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

import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
     * @version 1.0.3
     * @created 05-05-2026
     * @modified 06-05-2026
     * @description Registra un usuario local con nombre visible, password cifrada, aceptacion legal y bloqueo administrativo
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
        if (!isStrongPassword(request.password())) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña debe tener 8 caracteres, mayúscula, minúscula, número y símbolo"));
        }
        if (!Boolean.TRUE.equals(request.acceptPolicies())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debes aceptar las políticas para registrarte"));
        }
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ya existe una cuenta con ese email"));
        }
        if (appUserRepository.existsByNameIgnoreCase(username)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ya existe una cuenta con ese nombre de usuario"));
        }

        AppUser user = new AppUser();
        user.setName(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setProvider(AuthProvider.LOCAL);
        user.setPoliciesAccepted(true);
        user.setCookieConsent(false);
        appUserRepository.save(user);
        authenticate(user.getEmail(), request.password(), servletRequest);
        return ResponseEntity.ok(toResponse(user));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Inicia sesion por email, nombre de usuario o alias admin usando Spring Security
     * @param request Credenciales
     * @param servletRequest Peticion HTTP para almacenar la sesion
     * @return Usuario autenticado
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String login = resolveLogin(request.email());
        String emailToCheck = appUserRepository.findByEmailIgnoreCase(login)
                .or(() -> appUserRepository.findByNameIgnoreCase(login))
                .map(AppUser::getEmail)
                .orElse(normalizeEmail(login));
        if (blockedEmailRepository.existsByEmailIgnoreCase(emailToCheck)) {
            return ResponseEntity.status(403).body(Map.of("error", "Cuenta bloqueada por administración"));
        }
        Authentication authentication = authenticate(login, request.password(), servletRequest);
        AppUser user = appUserRepository.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(toResponse(user));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 07-05-2026
     * @modified 07-05-2026
     * @description Genera un token temporal y envia el enlace de restablecimiento por correo si la cuenta existe
     * @param request Email indicado por el usuario
     * @param servletRequest Peticion HTTP para construir la URL publica
     * @return Estado generico para no revelar si el email existe
     */
    @PostMapping("/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
        String email = normalizeEmail(request.email());
        boolean mailSent = false;
        boolean blocked = !isBlank(email) && blockedEmailRepository.existsByEmailIgnoreCase(email);
        if (!isBlank(email) && !blocked) {
            mailSent = appUserRepository.findByEmailIgnoreCase(email)
                    .map(user -> preparePasswordReset(user, servletRequest))
                    .orElse(false);
        }
        return ResponseEntity.ok(Map.of("requested", true, "mailSent", mailSent, "blocked", blocked));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Valida el token recibido por correo y guarda una contrasena nueva cifrada
     * @param request Token y contrasena nueva
     * @return Estado de guardado
     */
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@RequestBody PasswordResetConfirmRequest request) {
        if (isBlank(request.token()) || !isStrongPassword(request.password())) {
            return ResponseEntity.badRequest().body(Map.of("error", "El enlace no es válido o la contraseña no cumple los requisitos"));
        }
        AppUser user = appUserRepository.findByPasswordResetToken(request.token())
                .filter(candidate -> candidate.getPasswordResetExpiresAt() != null
                        && candidate.getPasswordResetExpiresAt().isAfter(Instant.now()))
                .orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El enlace de restablecimiento ha caducado o no es válido"));
        }
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        appUserRepository.save(user);
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

    private Map<String, Object> toResponse(AppUser user) {
        return Map.ofEntries(
                Map.entry("authenticated", true),
                Map.entry("id", user.getId()),
                Map.entry("name", user.getName()),
                Map.entry("email", user.getEmail()),
                Map.entry("role", user.getRole().name()),
                Map.entry("provider", user.getProvider().name()),
                Map.entry("cookieConsent", user.isCookieConsent()),
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

    public record RegisterRequest(String name, String email, String password, Boolean acceptPolicies) {}
    public record LoginRequest(String email, String password) {}
    public record PasswordResetRequest(String email) {}
    public record PasswordResetConfirmRequest(String token, String password) {}
    public record CookieRequest(Boolean accepted) {}
}

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.1
 * @created 12-05-2026
 * @modified 22-05-2026
 * @description Tests de validación de contraseña, recuperación y estados explícitos de cookies en autenticación
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.CookieConsentStatus;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.BlockedEmailRepository;
import com.yerai.racestream.service.PasswordResetMailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private AppUserRepository appUserRepository;
    private BlockedEmailRepository blockedEmailRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private PasswordResetMailService passwordResetMailService;
    private AuthController controller;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Prepara el controlador con dependencias mockeadas
     */
    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        blockedEmailRepository = mock(BlockedEmailRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        passwordResetMailService = mock(PasswordResetMailService.class);
        controller = new AuthController(
                appUserRepository,
                blockedEmailRepository,
                passwordEncoder,
                authenticationManager,
                passwordResetMailService);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica error de campo al intentar iniciar sesión con email no registrado
     */
    @Test
    void loginWithUnknownUserReturnsEmailFieldError() {
        var response = controller.login(new AuthController.LoginRequest("nadie@test.com", "RaceStream1!"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("field")).isEqualTo("email");
        assertThat(body.get("error")).isEqualTo("Usuario o email no registrado");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica error de campo al intentar iniciar sesión con contraseña incorrecta
     */
    @Test
    void loginWithWrongPasswordReturnsPasswordFieldError() {
        AppUser user = new AppUser();
        user.setEmail("user@test.com");
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad"));

        var response = controller.login(new AuthController.LoginRequest("user@test.com", "mal"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("field")).isEqualTo("password");
        assertThat(body.get("error")).isEqualTo("Contraseña incorrecta");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica error de campo al intentar registrar un email ya existente
     */
    @Test
    void registerWithDuplicatedEmailReturnsEmailFieldError() {
        when(appUserRepository.existsByEmailIgnoreCase("user@test.com")).thenReturn(true);

        var response = controller.register(
                new AuthController.RegisterRequest("Yerai", "user@test.com", "RaceStream1!", "RaceStream1!", true),
                null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("field")).isEqualTo("email");
        assertThat(body.get("error")).isEqualTo("Correo electrónico ya registrado");
        verify(appUserRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica error de campo al intentar registrar un nombre de usuario ya existente
     */
    @Test
    void registerWithDuplicatedNameReturnsNameFieldError() {
        when(appUserRepository.existsByNameIgnoreCase("Yerai")).thenReturn(true);

        var response = controller.register(
                new AuthController.RegisterRequest("Yerai", "user@test.com", "RaceStream1!", "RaceStream1!", true),
                null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("field")).isEqualTo("name");
        assertThat(body.get("error")).isEqualTo("Nombre de usuario ya registrado");
        verify(appUserRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @modified 22-05-2026
     * @description Verifica error de campo cuando el email de recuperación no existe
     */
    @Test
    void passwordResetRequestWithUnknownEmailReturnsEmailFieldError() {
        var response = controller.requestPasswordReset(
                new AuthController.PasswordResetRequest("nadie@test.com"),
                new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("field")).isEqualTo("email");
        assertThat(body.get("error")).isEqualTo("Email no registrado");
        verify(passwordResetMailService, never()).sendResetLink(org.mockito.ArgumentMatchers.any(), anyString());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @modified 22-05-2026
     * @description Verifica que SMTP desactivado solo aparece para emails registrados
     */
    @Test
    void passwordResetRequestForRegisteredEmailKeepsSmtpDisabledState() {
        AppUser user = new AppUser();
        user.setEmail("user@test.com");
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));
        when(passwordResetMailService.sendResetLink(org.mockito.ArgumentMatchers.eq(user), anyString())).thenReturn(false);

        var response = controller.requestPasswordReset(
                new AuthController.PasswordResetRequest("user@test.com"),
                new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("mailSent")).isEqualTo(false);
        assertThat(body.get("blocked")).isEqualTo(false);
        assertThat(user.getPasswordResetToken()).isNotBlank();
        assertThat(user.getPasswordResetExpiresAt()).isAfter(Instant.now());
        verify(appUserRepository).save(user);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @modified 22-05-2026
     * @description Verifica error de campo al intentar reutilizar la contraseña actual
     */
    @Test
    void passwordResetConfirmRejectsCurrentPasswordReuse() {
        AppUser user = userWithResetToken();
        when(appUserRepository.findByPasswordResetToken("token")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("RaceStream1!", "hash")).thenReturn(true);

        var response = controller.confirmPasswordReset(
                new AuthController.PasswordResetConfirmRequest("token", "RaceStream1!", "RaceStream1!"),
                null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("field")).isEqualTo("password");
        assertThat(body.get("error")).isEqualTo("Ya es tu contraseña actual");
        verify(appUserRepository, never()).save(user);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @modified 22-05-2026
     * @description Verifica que el enlace caduca al guardar una contraseña nueva
     */
    @Test
    void passwordResetConfirmExpiresTokenAfterSaving() {
        AppUser user = userWithResetToken();
        when(appUserRepository.findByPasswordResetToken("token")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("RaceStream2!", "hash")).thenReturn(false);
        when(passwordEncoder.encode("RaceStream2!")).thenReturn("new-hash");

        var response = controller.confirmPasswordReset(
                new AuthController.PasswordResetConfirmRequest("token", "RaceStream2!", "RaceStream2!"),
                null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.getPasswordResetToken()).isNull();
        assertThat(user.getPasswordResetExpiresAt()).isNull();
        verify(appUserRepository).save(user);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica mensaje claro para contraseña débil en registro
     */
    @Test
    void registerReturnsClearWeakPasswordError() {
        var response = controller.register(
                new AuthController.RegisterRequest("Yerai", "yerai@test.com", "weak", "weak", true),
                null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("La contraseña no cumple los requisitos");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica mensaje claro cuando la confirmacion no coincide
     */
    @Test
    void registerReturnsClearPasswordMismatchError() {
        var response = controller.register(
                new AuthController.RegisterRequest("Yerai", "yerai@test.com", "Aa123456!", "Bb123456!", true),
                null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("Las contraseñas no coinciden");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que aceptar cookies crea la cookie técnica
     */
    @Test
    void acceptCookiesCreatesAcceptedCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.cookies(new AuthController.CookieRequest(true), null, response);

        assertThat(response.getHeader("Set-Cookie")).contains("rs_cookie_consent=accepted");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que rechazar cookies crea la cookie técnica
     */
    @Test
    void rejectCookiesCreatesRejectedCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.cookies(new AuthController.CookieRequest(false), null, response);

        assertThat(response.getHeader("Set-Cookie")).contains("rs_cookie_consent=rejected");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que un usuario autenticado sincroniza su aceptación de cookies
     */
    @Test
    void authenticatedUserCookieAcceptanceIsSaved() {
        AppUser user = userWithCookieStatus(CookieConsentStatus.UNDECIDED);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));

        controller.cookies(new AuthController.CookieRequest(true), "user@test.com", response);

        assertThat(user.getCookieConsentStatus()).isEqualTo(CookieConsentStatus.ACCEPTED);
        verify(appUserRepository).save(user);
        assertThat(response.getHeader("Set-Cookie")).contains("rs_cookie_consent=accepted");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @description Verifica que un usuario autenticado sincroniza el rechazo de cookies
     */
    @Test
    void authenticatedUserCookieRejectionIsSaved() {
        AppUser user = userWithCookieStatus(CookieConsentStatus.UNDECIDED);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));

        controller.cookies(new AuthController.CookieRequest(false), "user@test.com", response);

        assertThat(user.getCookieConsentStatus()).isEqualTo(CookieConsentStatus.REJECTED);
        assertThat(user.isCookieConsent()).isFalse();
        verify(appUserRepository).save(user);
        assertThat(response.getHeader("Set-Cookie")).contains("rs_cookie_consent=rejected");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @description Verifica que /api/auth/me devuelve aceptación de cookies con estado explícito
     */
    @Test
    void meReturnsAcceptedCookieStatus() {
        AppUser user = userWithCookieStatus(CookieConsentStatus.ACCEPTED);
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));

        Map<?, ?> body = (Map<?, ?>) controller.me("user@test.com").getBody();

        assertThat(body.get("cookieConsent")).isEqualTo(true);
        assertThat(body.get("cookieConsentStatus")).isEqualTo("ACCEPTED");
        assertThat(body.get("cookieConsentDecided")).isEqualTo(true);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @description Verifica que un rechazo autenticado se puede restaurar desde /api/auth/me
     */
    @Test
    void meReturnsRejectedCookieStatusForRestore() {
        AppUser user = userWithCookieStatus(CookieConsentStatus.REJECTED);
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));

        Map<?, ?> body = (Map<?, ?>) controller.me("user@test.com").getBody();

        assertThat(body.get("cookieConsent")).isEqualTo(false);
        assertThat(body.get("cookieConsentStatus")).isEqualTo("REJECTED");
        assertThat(body.get("cookieConsentDecided")).isEqualTo(true);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @description Verifica que sin decisión no se confunde con rechazo
     */
    @Test
    void undecidedUserIsNotConfusedWithRejection() {
        AppUser user = userWithCookieStatus(CookieConsentStatus.UNDECIDED);
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));

        Map<?, ?> body = (Map<?, ?>) controller.me("user@test.com").getBody();

        assertThat(body.get("cookieConsent")).isEqualTo(false);
        assertThat(body.get("cookieConsentStatus")).isEqualTo("UNDECIDED");
        assertThat(body.get("cookieConsentDecided")).isEqualTo(false);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @description Crea un usuario válido para respuestas de sesión en tests unitarios
     * @param status Estado de cookies deseado
     * @return Usuario preparado
     */
    private AppUser userWithCookieStatus(CookieConsentStatus status) {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setName("Usuario");
        user.setEmail("user@test.com");
        user.setCookieConsentStatus(status);
        return user;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @modified 22-05-2026
     * @description Crea un usuario con token vigente para pruebas de recuperación
     * @return Usuario preparado para restablecimiento
     */
    private AppUser userWithResetToken() {
        AppUser user = new AppUser();
        user.setEmail("user@test.com");
        user.setPassword("hash");
        user.setPasswordResetToken("token");
        user.setPasswordResetExpiresAt(Instant.now().plusSeconds(60));
        return user;
    }
}

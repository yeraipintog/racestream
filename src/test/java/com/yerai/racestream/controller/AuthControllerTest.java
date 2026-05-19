/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 12-05-2026
 * @modified 18-05-2026
 * @description Tests de validacion de contrasena y estados explicitos de cookies en autenticacion
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.CookieConsentStatus;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.BlockedEmailRepository;
import com.yerai.racestream.service.PasswordResetMailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private AppUserRepository appUserRepository;
    private AuthenticationManager authenticationManager;
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
        authenticationManager = mock(AuthenticationManager.class);
        controller = new AuthController(
                appUserRepository,
                mock(BlockedEmailRepository.class),
                mock(PasswordEncoder.class),
                authenticationManager,
                mock(PasswordResetMailService.class));
    }

    @Test
    void loginWithUnknownUserReturnsEmailFieldError() {
        var response = controller.login(new AuthController.LoginRequest("nadie@test.com", "RaceStream1!"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("field")).isEqualTo("email");
        assertThat(body.get("error")).isEqualTo("Usuario o email no registrado");
    }

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
     * @created 12-05-2026
     * @description Verifica mensaje claro para contrasena debil en registro
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
     * @description Verifica que aceptar cookies crea la cookie tecnica
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
     * @description Verifica que rechazar cookies crea la cookie tecnica
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
     * @description Verifica que un usuario autenticado sincroniza su aceptacion de cookies
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
     * @description Verifica que /api/auth/me devuelve aceptacion de cookies con estado explicito
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
     * @description Verifica que sin decision no se confunde con rechazo
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
     * @description Crea un usuario valido para respuestas de sesion en tests unitarios
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
}

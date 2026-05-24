/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 12-05-2026
 * @modified 22-05-2026
 * @description Tests de validacion de contrasena en cuenta de usuario
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    private AppUserRepository appUserRepository;
    private PasswordEncoder passwordEncoder;
    private UserController controller;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Prepara controlador con dependencias mockeadas
     */
    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        controller = new UserController(appUserRepository, passwordEncoder);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica mensaje claro para contrasena debil al actualizar cuenta
     */
    @Test
    void passwordReturnsClearWeakPasswordError() {
        var response = controller.password(new UserController.PasswordRequest("weak", "weak"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("La contraseña no cumple los requisitos");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica mensaje claro cuando la confirmacion no coincide al actualizar cuenta
     */
    @Test
    void passwordReturnsClearMismatchError() {
        var response = controller.password(new UserController.PasswordRequest("Aa123456!", "Bb123456!"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("Las contraseñas no coinciden");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @modified 22-05-2026
     * @description Verifica que Mi Cuenta rechaza reutilizar la contrasena actual en el campo nuevo
     */
    @Test
    void passwordRejectsCurrentPasswordReuse() {
        AppUser user = new AppUser();
        user.setEmail("user@test.com");
        user.setPassword("hash");
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("RaceStream1!", "hash")).thenReturn(true);

        var response = controller.password(
                new UserController.PasswordRequest("RaceStream1!", "RaceStream1!", "RaceStream1!"),
                new UsernamePasswordAuthenticationToken("user@test.com", null));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("field")).isEqualTo("password");
        assertThat(body.get("error")).isEqualTo("Ya es tu contraseña actual");
        verify(appUserRepository, never()).save(user);
    }
}

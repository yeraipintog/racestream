/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 12-05-2026
 * @description Tests de validacion de contrasena en cuenta de usuario
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UserControllerTest {

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
        controller = new UserController(mock(AppUserRepository.class), mock(PasswordEncoder.class));
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
}

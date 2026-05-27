/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 27-05-2026
 * @modified 27-05-2026
 * @description Tests del acceso público a temporadas para bloquear históricos
 *              a usuarios anónimos y permitirlos con sesión iniciada
 */
package com.yerai.racestream.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;

class PublicSeasonAccessServiceTest {

    private final PublicSeasonAccessService service = new PublicSeasonAccessService();

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 27-05-2026
     * @modified 27-05-2026
     * @description Verifica que un usuario anónimo no pueda forzar histórico por
     *              URL o API
     */
    @Test
    void anonymousUserIsForcedToCurrentSeason() {
        assertThat(service.resolveYear(1950, null)).isEqualTo(Year.now().getValue());
        assertThat(service.resolveYear(2005, "anonymousUser")).isEqualTo(Year.now().getValue());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 27-05-2026
     * @modified 27-05-2026
     * @description Verifica que una sesión real conserve el filtro histórico
     *              solicitado
     */
    @Test
    void authenticatedUserCanRequestHistoricalSeason() {
        Object principal = User.withUsername("piloto@racestream.local")
                .password("password")
                .roles("USER")
                .build();

        assertThat(service.resolveYear(2005, principal)).isEqualTo(2005);
    }
}

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 05-05-2026
 * @description Configuracion de cifrado de contrasenas para usuarios locales
 */
package com.yerai.racestream.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Crea el cifrador BCrypt para no guardar contrasenas en claro
     * @return PasswordEncoder BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

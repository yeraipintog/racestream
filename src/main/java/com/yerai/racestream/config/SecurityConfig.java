/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.2
 * @created 09-03-2026
 * @modified 27-04-2026
 * @description Configuracion de seguridad abierta para desarrollo local del TFG
 */
package com.yerai.racestream.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 09-03-2026
     * @modified 30-04-2026
     * @description Configuración de seguridad abierta para desarrollo local del TFG
     * @see https://spring.io/guides/gs/securing-web
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}

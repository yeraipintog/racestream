/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 09-03-2026
 * @modified 27-04-2026
 * @description Configuración de seguridad con rutas demo retiradas
 */
package com.yerai.racestream.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/calendar.html", "/css/**", "/js/**", "/assets/**", "/api/**")
                        .permitAll()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}

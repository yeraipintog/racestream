/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.0
 * @created 09-03-2026
 * @modified 05-05-2026
 * @description Configuracion de seguridad con zonas publicas, privadas y roles
 */
package com.yerai.racestream.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.2.0
     * @created 09-03-2026
     * @modified 05-05-2026
     * @description Protege paginas privadas, APIs de usuario y administracion
     * @param http Constructor de seguridad HTTP
     * @return Cadena de filtros configurada
     * @throws Exception Si la configuracion no puede construirse
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/live.html", "/calendar.html", "/sessions.html",
                                "/standings.html", "/drivers.html", "/teams.html", "/news.html", "/help.html",
                                "/faq.html", "/terms.html", "/privacy-policy.html", "/cookies.html", "/login.html",
                                "/register.html", "/css/**", "/js/**", "/assets/**", "/api/f1/**",
                                "/api/news/**", "/api/auth/**").permitAll()
                        .requestMatchers("/forum.html", "/contact.html", "/account.html", "/favorites.html",
                                "/preferences.html", "/privacy.html", "/api/forum/**", "/api/contact/**",
                                "/api/user/**", "/api/favorites/**").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/api/auth/login-form")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/account.html", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessUrl("/login.html?logout")
                        .permitAll())
                .exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, authException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(401);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.getWriter().write("{\"error\":\"Autenticacion requerida\"}");
                        return;
                    }
                    response.sendRedirect("/login.html");
                }));

        return http.build();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Expone el AuthenticationManager para login JSON
     * @param configuration Configuracion de autenticacion
     * @return AuthenticationManager configurado
     * @throws Exception Si Spring Security no puede construirlo
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}

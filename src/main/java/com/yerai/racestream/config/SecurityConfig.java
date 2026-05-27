/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.3.4
 * @created 09-03-2026
 * @modified 26-05-2026
 * @description Configuración de seguridad con zonas públicas, Live privado,
 *              CSRF por cookie, diagnóstico ADMIN y login JSON propio
 */
package com.yerai.racestream.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.3.2
     * @created 09-03-2026
     * @modified 26-05-2026
     * @description Protege páginas privadas, Live Center, APIs de usuario y
     *              administración, manteniendo público el estado Live ligero y
     *              emitiendo token CSRF para formularios JSON
     * @param http Constructor de seguridad HTTP
     * @return Cadena de filtros configurada
     * @throws Exception Si la configuración no puede construirse
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/f1/live/status").permitAll()
                        .requestMatchers("/live.html", "/live-timing.html", "/live-race.html", "/api/f1/live/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/f1/media/team-radio").authenticated()
                        .requestMatchers("/api/f1/diagnostics/**").hasRole("ADMIN")
                        .requestMatchers("/", "/index.html", "/calendar.html", "/sessions.html",
                                "/standings.html", "/drivers.html", "/teams.html", "/news.html", "/help.html",
                                "/faq.html", "/terms.html", "/privacy-policy.html", "/cookies.html", "/login.html",
                                "/register.html", "/css/**", "/js/**", "/assets/**", "/api/f1/**",
                                "/api/news/**", "/api/auth/**").permitAll()
                        .requestMatchers("/forum.html", "/contact.html", "/account.html", "/favorites.html",
                                "/preferences.html", "/privacy.html", "/api/forum/**", "/api/contact/**",
                                "/api/user/**", "/api/favorites/**").authenticated()
                        .requestMatchers("/admin.html", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .formLogin(form -> form.disable())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .addLogoutHandler((request, response, authentication) -> {
                            SecurityContextHolder.clearContext();
                            HttpSession session = request.getSession(false);
                            if (session != null) {
                                session.invalidate();
                            }
                            ResponseCookie cookie = ResponseCookie.from("JSESSIONID", "")
                                    .path("/")
                                    .maxAge(0)
                                    .build();
                            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
                        })
                        .logoutSuccessUrl("/login.html?logout")
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, authException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(401);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.getWriter().write("{\"error\":\"Autenticación requerida\"}");
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
     * @param configuration Configuración de autenticación
     * @return AuthenticationManager configurado
     * @throws Exception Si Spring Security no puede construirlo
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 26-05-2026
     * @modified 26-05-2026
     * @description Fuerza la emisión de la cookie XSRF-TOKEN para que el
     *              frontend pueda proteger peticiones JSON
     */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {

        /**
         * @author Yerai Pinto
         * @since 1.0
         * @version 1.0.0
         * @created 26-05-2026
         * @modified 26-05-2026
         * @description Materializa el token CSRF diferido y continúa la cadena
         * @param request Petición HTTP
         * @param response Respuesta HTTP
         * @param filterChain Cadena de filtros
         * @throws ServletException Si falla el filtro
         * @throws IOException Si falla la respuesta
         */
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 13-05-2026
 * @modified 13-05-2026
 * @description Fuerza cabeceras no-cache en respuestas API para evitar datos obsoletos en navegador
 */
package com.yerai.racestream.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiNoCacheFilter extends OncePerRequestFilter {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Añade cabeceras no-cache solo a rutas /api/
     * @param request Petición HTTP
     * @param response Respuesta HTTP
     * @param filterChain Cadena de filtros
     * @throws ServletException Si falla el filtro
     * @throws IOException Si falla la respuesta
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, max-age=0, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
        }
        filterChain.doFilter(request, response);
    }
}

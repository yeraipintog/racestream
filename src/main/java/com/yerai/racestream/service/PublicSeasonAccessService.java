/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.0
 * @created 20-05-2026
 * @modified 27-05-2026
 * @description Resuelve la temporada permitida: los usuarios anónimos solo
 *              acceden a la temporada actual y los autenticados pueden usar
 *              filtros históricos
 */
package com.yerai.racestream.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Year;

@Service
public class PublicSeasonAccessService {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.2.0
     * @created 20-05-2026
     * @modified 27-05-2026
     * @description Devuelve la temporada actual para usuarios anónimos y respeta
     *              la solicitada solo cuando hay sesión iniciada
     * @param requestedYear Temporada pedida por el cliente
     * @param principal Usuario autenticado o anónimo, conservado por compatibilidad con controladores
     * @return Temporada permitida
     */
    public Integer resolveYear(Integer requestedYear, Object principal) {
        int currentYear = Year.now().getValue();
        if (!isAuthenticated(principal)) {
            return currentYear;
        }
        return requestedYear == null ? currentYear : requestedYear;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 27-05-2026
     * @modified 27-05-2026
     * @description Distingue usuarios reales del principal anónimo de Spring
     *              Security
     * @param principal Principal recibido en el controlador
     * @return true si corresponde a un usuario autenticado
     */
    private boolean isAuthenticated(Object principal) {
        if (principal instanceof UserDetails) {
            return true;
        }
        if (principal instanceof String value) {
            return !value.isBlank() && !"anonymousUser".equals(value);
        }
        return principal != null;
    }
}

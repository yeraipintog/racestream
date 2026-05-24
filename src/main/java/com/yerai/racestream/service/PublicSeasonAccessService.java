/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 20-05-2026
 * @modified 20-05-2026
 * @description Aplica el limite de temporada actual para visitantes sin sesion
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Year;

@Service
public class PublicSeasonAccessService {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 20-05-2026
     * @modified 20-05-2026
     * @description Devuelve la temporada solicitada solo si hay sesion; en publico fuerza la actual
     * @param requestedYear Temporada pedida por el cliente
     * @param principal Usuario autenticado o anonimo
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
     * @created 20-05-2026
     * @modified 20-05-2026
     * @description Devuelve un listado de temporadas limitado a la actual para usuarios anonimos
     * @return Nodo JSON compatible con el selector existente
     */
    public JsonNode currentSeasonOnly() {
        ArrayNode seasons = JsonNodeFactory.instance.arrayNode();
        seasons.addObject().put("season", Year.now().getValue());
        return seasons;
    }

    public boolean isAuthenticated(Object principal) {
        if (principal instanceof UserDetails) {
            return true;
        }
        return principal instanceof String value && !value.isBlank() && !"anonymousUser".equals(value);
    }
}

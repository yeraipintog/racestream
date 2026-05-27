/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.0
 * @created 17-04-2026
 * @modified 27-05-2026
 * @description Controlador de OpenF1 con temporada actual pública y filtros
 *              históricos reservados a usuarios autenticados
 * @see https://openf1.org
 */
package com.yerai.racestream.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerai.racestream.service.OpenF1Service;
import com.yerai.racestream.service.PublicSeasonAccessService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/f1")
public class OpenF1Controller {

    private final OpenF1Service openF1Service;
    private final PublicSeasonAccessService publicSeasonAccessService;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 27-05-2026
     * @modified 27-05-2026
     * @description Inyecta el cliente OpenF1 y el control de acceso por temporada
     * @param openF1Service Servicio OpenF1
     * @param publicSeasonAccessService Servicio de bloqueo de históricos para invitados
     */
    public OpenF1Controller(OpenF1Service openF1Service, PublicSeasonAccessService publicSeasonAccessService) {
        this.openF1Service = openF1Service;
        this.publicSeasonAccessService = publicSeasonAccessService;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 17-04-2026
     * @modified 13-05-2026
     * @description Obtener meetings respetando temporada pública o autenticada
     * @param year Temporada solicitada
     * @param principal Usuario autenticado o anónimo
     * @return Meetings permitidos
     */
    @GetMapping("/meetings")
    public JsonNode getMeetings(@RequestParam(required = false) Integer year, @AuthenticationPrincipal Object principal) {
        return openF1Service.getMeetings(publicSeasonAccessService.resolveYear(year, principal));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 17-04-2026
     * @description Obtener sesiones
     * @param meetingKey Clave del meeting
     * @return Sesiones OpenF1
     */
    @GetMapping("/sessions")
    public JsonNode getSessions(@RequestParam Integer meetingKey) {
        return openF1Service.getSessions(meetingKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 17-04-2026
     * @modified 22-05-2026
     * @description Obtener pilotos por sesión numerica o latest
     * @param sessionKey Clave OpenF1
     * @return Pilotos
     */
    @GetMapping("/drivers")
    public JsonNode getDrivers(@RequestParam String sessionKey) {
        return openF1Service.getDrivers(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 17-04-2026
     * @description Obtener resultados de la sesión
     * @param sessionKey Clave OpenF1
     * @return Resultados de la sesión
     */
    @GetMapping("/session-results")
    public JsonNode getSessionResults(@RequestParam String sessionKey) {
        return openF1Service.getSessionResults(sessionKey);
    }
}

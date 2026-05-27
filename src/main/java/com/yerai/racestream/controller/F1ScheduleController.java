/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.0
 * @created 21-04-2026
 * @modified 27-05-2026
 * @description Controlador de F1Schedule con calendario enriquecido por OpenF1,
 *              Jolpica y F1DB, respetando el bloqueo de temporadas públicas
 * @see https://openf1.org
 */
package com.yerai.racestream.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerai.racestream.service.F1ScheduleService;
import com.yerai.racestream.service.PublicSeasonAccessService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/f1/schedule")
@CrossOrigin(origins = "*")
public class F1ScheduleController {

    private final F1ScheduleService f1ScheduleService;
    private final PublicSeasonAccessService publicSeasonAccessService;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 27-05-2026
     * @modified 27-05-2026
     * @description Inyecta servicios de calendario y control de acceso por temporada
     * @param f1ScheduleService Servicio de calendario F1
     * @param publicSeasonAccessService Servicio de bloqueo de históricos para invitados
     */
    public F1ScheduleController(F1ScheduleService f1ScheduleService, PublicSeasonAccessService publicSeasonAccessService) {
        this.f1ScheduleService = f1ScheduleService;
        this.publicSeasonAccessService = publicSeasonAccessService;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 13-05-2026
     * @description Obtener meetings
     * @param year Temporada solicitada
     * @param principal Usuario autenticado o anónimo
     * @return Meetings permitidos
     */
    @GetMapping("/meetings")
    public JsonNode getMeetings(@RequestParam(required = false) Integer year, @AuthenticationPrincipal Object principal) {
        return f1ScheduleService.getMeetingsByYear(publicSeasonAccessService.resolveYear(year, principal));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 28-04-2026
     * @modified 13-05-2026
     * @description Obtener meetings enriquecidos para la página Calendario
     * @param year Temporada solicitada
     * @param principal Usuario autenticado o anónimo
     * @return Meetings enriquecidos permitidos
     */
    @GetMapping("/calendar-meetings")
    public JsonNode getCalendarMeetings(@RequestParam(required = false) Integer year, @AuthenticationPrincipal Object principal) {
        return f1ScheduleService.getCalendarMeetingsByYear(publicSeasonAccessService.resolveYear(year, principal));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 13-05-2026
     * @description Obtener meeting por clave
     * @param meetingKey Clave del meeting
     * @return Meeting encontrado
     */
    @GetMapping("/meetings/{meetingKey}")
    public JsonNode getMeetingByKey(@PathVariable Integer meetingKey) {
        return f1ScheduleService.getMeetingByKey(meetingKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener sesiones por meeting
     * @param meetingKey Clave del meeting
     * @return Sesiones del meeting
     */
    @GetMapping("/meetings/{meetingKey}/sessions")
    public JsonNode getSessionsByMeeting(@PathVariable Integer meetingKey) {
        return f1ScheduleService.getSessionsByMeeting(meetingKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener siguiente meeting
     * @param year Temporada solicitada
     * @param principal Usuario autenticado o anónimo
     * @return Siguiente meeting permitido
     */
    @GetMapping("/next-meeting")
    public JsonNode getNextMeeting(@RequestParam(required = false) Integer year, @AuthenticationPrincipal Object principal) {
        return f1ScheduleService.getNextMeeting(publicSeasonAccessService.resolveYear(year, principal));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 13-05-2026
     * @description Obtener meeting actual o siguiente
     * @param year Temporada solicitada
     * @param principal Usuario autenticado o anónimo
     * @return Meeting actual o siguiente permitido
     */
    @GetMapping("/current-or-next-meeting")
    public JsonNode getCurrentOrNextMeeting(@RequestParam(required = false) Integer year, @AuthenticationPrincipal Object principal) {
        return f1ScheduleService.getCurrentOrNextMeeting(publicSeasonAccessService.resolveYear(year, principal));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 13-05-2026
     * @description Obtener siguiente sesión
     * @param year Temporada solicitada
     * @param principal Usuario autenticado o anónimo
     * @return Siguiente sesión permitida
     */
    @GetMapping("/next-session")
    public JsonNode getNextSession(@RequestParam(required = false) Integer year, @AuthenticationPrincipal Object principal) {
        return f1ScheduleService.getNextSession(publicSeasonAccessService.resolveYear(year, principal));
    }
}

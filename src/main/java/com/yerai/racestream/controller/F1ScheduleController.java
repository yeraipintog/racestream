/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 21-04-2026
 * @modified 13-05-2026
 * @description Controlador de F1Schedule con calendario enriquecido por Jolpica y limite publico de temporada
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
     * @param year
     * @return
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
     * @description Obtener meetings enriquecidos para la pagina Calendario
     * @param year
     * @return
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
     * @param meetingKey
     * @return
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
     * @param meetingKey
     * @return
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
     * @param year
     * @return
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
     * @param year
     * @return
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
     * @param year
     * @return
     */
    @GetMapping("/next-session")
    public JsonNode getNextSession(@RequestParam(required = false) Integer year, @AuthenticationPrincipal Object principal) {
        return f1ScheduleService.getNextSession(publicSeasonAccessService.resolveYear(year, principal));
    }
}

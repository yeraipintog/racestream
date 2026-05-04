/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.2
 * @created 21-04-2026
 * @modified 30-04-2026
 * @description Controlador de F1Schedule con calendario enriquecido por Jolpica
 * @see https://openf1.org
 */
package com.yerai.racestream.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerai.racestream.service.F1ScheduleService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/f1/schedule")
@CrossOrigin(origins = "*")
public class F1ScheduleController {

    private final F1ScheduleService f1ScheduleService;

    public F1ScheduleController(F1ScheduleService f1ScheduleService) {
        this.f1ScheduleService = f1ScheduleService;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener meetings
     * @param year
     * @return
     */
    @GetMapping("/meetings")
    public JsonNode getMeetings(@RequestParam(defaultValue = "2026") Integer year) {
        return f1ScheduleService.getMeetingsByYear(year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Obtener meetings enriquecidos para la pagina Calendario
     * @param year
     * @return
     */
    @GetMapping("/calendar-meetings")
    public JsonNode getCalendarMeetings(@RequestParam(defaultValue = "2026") Integer year) {
        return f1ScheduleService.getCalendarMeetingsByYear(year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
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
    public JsonNode getNextMeeting(@RequestParam(defaultValue = "2026") Integer year) {
        return f1ScheduleService.getNextMeeting(year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener meeting actual o siguiente
     * @param year
     * @return
     */
    @GetMapping("/current-or-next-meeting")
    public JsonNode getCurrentOrNextMeeting(@RequestParam(defaultValue = "2026") Integer year) {
        return f1ScheduleService.getCurrentOrNextMeeting(year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener siguiente sesión
     * @param year
     * @return
     */
    @GetMapping("/next-session")
    public JsonNode getNextSession(@RequestParam(defaultValue = "2026") Integer year) {
        return f1ScheduleService.getNextSession(year);
    }
}

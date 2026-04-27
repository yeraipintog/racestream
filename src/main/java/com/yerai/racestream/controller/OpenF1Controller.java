/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 17-04-2026
 * @description Controlador de OpenF1
 * @see https://openf1.org
 */
package com.yerai.racestream.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerai.racestream.service.OpenF1Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/f1")
public class OpenF1Controller {

    private final OpenF1Service openF1Service;

    public OpenF1Controller(OpenF1Service openF1Service) {
        this.openF1Service = openF1Service;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 17-04-2026
     * @description Obtener meetings
     * @param year
     * @return
     */
    @GetMapping("/meetings")
    public JsonNode getMeetings(@RequestParam(defaultValue = "2026") Integer year) {
        return openF1Service.getMeetings(year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 17-04-2026
     * @description Obtener sesiones
     * @param meetingKey
     * @return
     */
    @GetMapping("/sessions")
    public JsonNode getSessions(@RequestParam Integer meetingKey) {
        return openF1Service.getSessions(meetingKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 17-04-2026
     * @description Obtener pilotos
     * @param sessionKey
     * @return
     */
    @GetMapping("/drivers")
    public JsonNode getDrivers(@RequestParam Integer sessionKey) {
        return openF1Service.getDrivers(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 17-04-2026
     * @description Obtener resultados de la sesión
     * @param sessionKey
     * @return
     */
    @GetMapping("/session-results")
    public JsonNode getSessionResults(@RequestParam Integer sessionKey) {
        return openF1Service.getSessionResults(sessionKey);
    }
}
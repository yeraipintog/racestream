/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.3
 * @created 12-05-2026
 * @modified 24-05-2026
 * @description Controlador de diagnóstico para revisar disponibilidad de datos F1 por temporada en desarrollo
 */
package com.yerai.racestream.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yerai.racestream.service.F1DbService;
import com.yerai.racestream.service.F1ScheduleService;
import com.yerai.racestream.service.JolpicaService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/f1/diagnostics")
@CrossOrigin(origins = "*")
public class F1DiagnosticsController {

    private final F1ScheduleService f1ScheduleService;
    private final F1DbService f1DbService;
    private final JolpicaService jolpicaService;
    private final ObjectMapper objectMapper;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 12-05-2026
     * @modified 24-05-2026
     * @description Constructor con servicios de datos F1
     * @param f1ScheduleService Servicio de calendario y sesiones
     * @param f1DbService       Servicio F1DB
     * @param jolpicaService    Servicio Jolpica
     * @param objectMapper      Constructor JSON
     */
    public F1DiagnosticsController(
            F1ScheduleService f1ScheduleService,
            F1DbService f1DbService,
            JolpicaService jolpicaService,
            ObjectMapper objectMapper) {
        this.f1ScheduleService = f1ScheduleService;
        this.f1DbService = f1DbService;
        this.jolpicaService = jolpicaService;
        this.objectMapper = objectMapper;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 12-05-2026
     * @modified 24-05-2026
     * @description Devuelve conteos, estados y avisos de disponibilidad por fuente
     * @param year Temporada
     * @return Diagnostico JSON
     */
    @GetMapping("/season")
    public JsonNode getSeasonDiagnostics(@RequestParam(required = false) Integer year) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        ObjectNode checks = objectMapper.createObjectNode();
        ArrayNode warnings = objectMapper.createArrayNode();
        ArrayNode openF1Meetings = checkedArray(checks, "openf1_meetings", () -> (ArrayNode) f1ScheduleService.getMeetingsByYear(selectedYear), warnings);
        ArrayNode jolpicaRaces = checkedArray(checks, "jolpica_calendario", () -> jolpicaService.getRacesByYear(selectedYear), warnings);
        ArrayNode f1DbCircuits = checkedArray(checks, "f1db_circuitos", () -> f1DbService.getCircuits(selectedYear), warnings);
        ArrayNode meetings = checkedArray(checks, "meetings_calendario", () -> (ArrayNode) f1ScheduleService.getCalendarMeetingsByYear(selectedYear), warnings);
        ArrayNode drivers = checkedArray(checks, "standings_pilotos", () -> jolpicaService.getDriverStandingsByYear(selectedYear), warnings);
        ArrayNode constructors = checkedArray(checks, "standings_constructores", () -> jolpicaService.getConstructorStandingsByYear(selectedYear), warnings);
        ArrayNode races = checkedArray(checks, "resultados_carrera", () -> jolpicaService.getRaceResultsByYear(selectedYear), warnings);
        ArrayNode driverTitles = checkedArray(checks, "mundiales_pilotos", jolpicaService::getDriverWorldTitles, warnings);
        ArrayNode constructorTitles = checkedArray(checks, "mundiales_constructores", jolpicaService::getConstructorWorldTitles, warnings);
        ArrayNode sessionsByGp = objectMapper.createArrayNode();
        int sessionsTotal = 0;

        for (JsonNode meeting : meetings) {
            ObjectNode row = objectMapper.createObjectNode();
            int meetingKey = meeting.path("meeting_key").asInt(0);
            JsonNode cancelledSessions = meeting.path("cancelled_sessions");
            ArrayNode sessions = cancelledSessions.isArray()
                    ? (ArrayNode) cancelledSessions
                    : safeArray(() -> (ArrayNode) f1ScheduleService.getSessionsByMeeting(meetingKey));
            int sessionsCount = sessions.size();
            sessionsTotal += sessionsCount;

            row.put("meeting_key", meetingKey);
            row.put("meeting_name", meeting.path("meeting_name").asText("Gran Premio"));
            row.put("sessions_count", sessionsCount);
            row.put("sessions_checked", true);
            row.put("source", meeting.path("is_jolpica_fallback").asBoolean(false) ? "Jolpica/fallback" : "OpenF1/Jolpica");
            row.put("cancelled", meeting.path("is_cancelled").asBoolean(false));
            sessionsByGp.add(row);

            if (meetingKey == 0) {
                warnings.add("GP sin clave de sesiones: " + row.path("meeting_name").asText());
            }
            if (meeting.path("is_jolpica_fallback").asBoolean(false)) {
                warnings.add("GP sintetico Jolpica: " + row.path("meeting_name").asText());
            }
        }

        if (meetings.isEmpty()) {
            warnings.add("Temporada sin meetings disponibles.");
        }
        if (openF1Meetings.isEmpty()) {
            warnings.add("OpenF1 no ha devuelto meetings.");
        }
        if (jolpicaRaces.isEmpty()) {
            warnings.add("Jolpica no ha devuelto carreras.");
        }
        if (f1DbCircuits.isEmpty()) {
            warnings.add("F1DB no ha devuelto circuitos.");
        }
        if (drivers.isEmpty()) {
            warnings.add("Temporada sin clasificacion de pilotos.");
        }
        if (constructors.isEmpty()) {
            warnings.add(selectedYear < 1958
                    ? "Temporada anterior al campeonato de constructores."
                    : "Temporada sin clasificacion de escuderias.");
        }
        if (races.isEmpty()) {
            warnings.add("Temporada sin resultados de carrera.");
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("year", selectedYear);
        response.put("meetings_count", meetings.size());
        response.put("openf1_meetings_count", openF1Meetings.size());
        response.put("jolpica_races_count", jolpicaRaces.size());
        response.put("f1db_circuits_count", f1DbCircuits.size());
        response.put("fallback_meetings_count", meetings.findValues("is_jolpica_fallback").stream()
                .filter(JsonNode::asBoolean)
                .count());
        response.put("sessions_checked", false);
        response.set("sessions_by_gp", sessionsByGp);
        response.put("drivers_count", drivers.size());
        response.put("constructors_count", constructors.size());
        response.put("race_results_count", races.size());
        response.put("driver_titles_count", driverTitles.size());
        response.put("constructor_titles_count", constructorTitles.size());
        response.put("sessions_count", sessionsTotal);
        response.set("checks", checks);
        response.set("warnings", warnings);
        response.put("source_used", selectedYear >= 2023 ? "OpenF1/Jolpica/F1DB/fallback" : "Jolpica/F1DB/fallback");
        return response;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Ejecuta una comprobación y registra estado resumido sin romper todo el diagnóstico
     * @param checks Nodo de estados
     * @param name Nombre de la comprobacion
     * @param supplier Fuente de datos
     * @param warnings Avisos acumulados
     * @return Array seguro
     */
    private ArrayNode checkedArray(ObjectNode checks, String name, Supplier<ArrayNode> supplier, ArrayNode warnings) {
        ObjectNode check = objectMapper.createObjectNode();
        try {
            ArrayNode result = supplier.get();
            ArrayNode safe = result == null ? objectMapper.createArrayNode() : result;
            check.put("count", safe.size());
            check.put("status", safe.isEmpty() ? "empty_real" : "loaded");
            checks.set(name, check);
            return safe;
        } catch (RuntimeException ex) {
            check.put("count", 0);
            check.put("status", "error");
            check.put("error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            checks.set(name, check);
            warnings.add(name + ": " + check.path("error").asText());
            return objectMapper.createArrayNode();
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Ejecuta una fuente opcional y devuelve array vacío si falla
     * @param supplier Fuente de datos
     * @return Array seguro
     */
    private ArrayNode safeArray(Supplier<ArrayNode> supplier) {
        try {
            ArrayNode result = supplier.get();
            return result == null ? objectMapper.createArrayNode() : result;
        } catch (RuntimeException ex) {
            return objectMapper.createArrayNode();
        }
    }
}

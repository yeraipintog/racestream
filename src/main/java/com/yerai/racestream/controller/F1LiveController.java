package com.yerai.racestream.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerai.racestream.service.F1LiveService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 21-04-2026
 * @modified 27-04-2026
 * @description Controlador REST para exponer datos del Live Center de Formula 1 desde OpenF1
 */
@RestController
@RequestMapping("/api/f1/live")
@CrossOrigin(origins = "*")
public class F1LiveController {

    private final F1LiveService f1LiveService;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Constructor con inyeccion del servicio live
     * @param f1LiveService Servicio de datos en vivo
     */
    public F1LiveController(F1LiveService f1LiveService) {
        this.f1LiveService = f1LiveService;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve un resumen combinado del Live Center
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos live agrupados
     */
    @GetMapping("/overview")
    public JsonNode getLiveOverview(@RequestParam String sessionKey) {
        return f1LiveService.getLiveOverview(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve la meteorologia de la sesion
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de weather
     */
    @GetMapping("/weather")
    public JsonNode getWeather(@RequestParam String sessionKey) {
        return f1LiveService.getWeather(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve mensajes de direccion de carrera
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de race control
     */
    @GetMapping("/race-control")
    public JsonNode getRaceControl(@RequestParam String sessionKey) {
        return f1LiveService.getRaceControl(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve las paradas en boxes registradas
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de pit stops
     */
    @GetMapping("/pit-stops")
    public JsonNode getPitStops(@RequestParam String sessionKey) {
        return f1LiveService.getPitStops(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve los stints y neumaticos de la sesion
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de stints
     */
    @GetMapping("/stints")
    public JsonNode getStints(@RequestParam String sessionKey) {
        return f1LiveService.getStints(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve radios de equipo disponibles
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de team radio
     */
    @GetMapping("/team-radio")
    public JsonNode getTeamRadio(@RequestParam String sessionKey) {
        return f1LiveService.getTeamRadio(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve posiciones de pilotos
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de posicion
     */
    @GetMapping("/position")
    public JsonNode getPosition(@RequestParam String sessionKey) {
        return f1LiveService.getPosition(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve gaps e intervalos entre pilotos
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de intervalos
     */
    @GetMapping("/intervals")
    public JsonNode getIntervals(@RequestParam String sessionKey) {
        return f1LiveService.getIntervals(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve vueltas registradas
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de vueltas
     */
    @GetMapping("/laps")
    public JsonNode getLaps(@RequestParam String sessionKey) {
        return f1LiveService.getLaps(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve adelantamientos registrados
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de adelantamientos
     */
    @GetMapping("/overtakes")
    public JsonNode getOvertakes(@RequestParam String sessionKey) {
        return f1LiveService.getOvertakes(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve la parrilla de salida
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de parrilla
     */
    @GetMapping("/starting-grid")
    public JsonNode getStartingGrid(@RequestParam String sessionKey) {
        return f1LiveService.getStartingGrid(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve clasificacion de pilotos si OpenF1 la expone para la sesion
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de clasificacion de pilotos
     */
    @GetMapping("/championship-drivers")
    public JsonNode getChampionshipDrivers(@RequestParam String sessionKey) {
        return f1LiveService.getChampionshipDrivers(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve clasificacion de constructores si OpenF1 la expone para la sesion
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de clasificacion de escuderias
     */
    @GetMapping("/championship-teams")
    public JsonNode getChampionshipTeams(@RequestParam String sessionKey) {
        return f1LiveService.getChampionshipTeams(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve telemetria del coche para toda la sesion o un piloto concreto
     * @param sessionKey Clave de sesion OpenF1
     * @param driverNumber Numero del piloto opcional
     * @return Datos de car data
     */
    @GetMapping("/car-data")
    public JsonNode getCarData(@RequestParam String sessionKey,
            @RequestParam(required = false) Integer driverNumber) {
        return f1LiveService.getCarData(sessionKey, driverNumber);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Devuelve ubicacion en pista para toda la sesion o un piloto concreto
     * @param sessionKey Clave de sesion OpenF1
     * @param driverNumber Numero del piloto opcional
     * @return Datos de location
     */
    @GetMapping("/location")
    public JsonNode getLocation(@RequestParam String sessionKey,
            @RequestParam(required = false) Integer driverNumber) {
        return f1LiveService.getLocation(sessionKey, driverNumber);
    }
}

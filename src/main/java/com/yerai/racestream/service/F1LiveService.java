/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 21-04-2026
 * @modified 27-04-2026
 * @description Servicio del Live Center que agrupa y delega datos en vivo de
 *              OpenF1
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

@Service
public class F1LiveService {

    private final OpenF1Service openF1Service;
    private final ObjectMapper objectMapper;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Constructor con dependencias necesarias para consultar OpenF1 y
     *              construir respuestas JSON
     * @param openF1Service Servicio base de OpenF1
     * @param objectMapper  Mapper para construir respuestas agregadas
     */
    public F1LiveService(OpenF1Service openF1Service, ObjectMapper objectMapper) {
        this.openF1Service = openF1Service;
        this.objectMapper = objectMapper;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene meteorologia de la sesion
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de weather
     */
    public JsonNode getWeather(String sessionKey) {
        return openF1Service.getWeather(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene mensajes de direccion de carrera
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de race control
     */
    public JsonNode getRaceControl(String sessionKey) {
        return openF1Service.getRaceControl(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene paradas en boxes
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de pit stops
     */
    public JsonNode getPitStops(String sessionKey) {
        return openF1Service.getPitStops(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene stints y neumaticos
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de stints
     */
    public JsonNode getStints(String sessionKey) {
        return openF1Service.getStints(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene radios de equipo
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de team radio
     */
    public JsonNode getTeamRadio(String sessionKey) {
        return openF1Service.getTeamRadio(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene posiciones de pilotos
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de posicion
     */
    public JsonNode getPosition(String sessionKey) {
        return openF1Service.getPosition(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene gaps e intervalos
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de intervalos
     */
    public JsonNode getIntervals(String sessionKey) {
        return openF1Service.getIntervals(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene vueltas de la sesion
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de vueltas
     */
    public JsonNode getLaps(String sessionKey) {
        return openF1Service.getLaps(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene adelantamientos
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de adelantamientos
     */
    public JsonNode getOvertakes(String sessionKey) {
        return openF1Service.getOvertakes(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene parrilla de salida
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de parrilla
     */
    public JsonNode getStartingGrid(String sessionKey) {
        return openF1Service.getStartingGrid(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene clasificacion de pilotos si esta disponible
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de clasificacion de pilotos
     */
    public JsonNode getChampionshipDrivers(String sessionKey) {
        return openF1Service.getChampionshipDrivers(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene clasificacion de constructores si esta disponible
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos de clasificacion de escuderias
     */
    public JsonNode getChampionshipTeams(String sessionKey) {
        return openF1Service.getChampionshipTeams(sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene telemetria del coche
     * @param sessionKey   Clave de sesion OpenF1
     * @param driverNumber Numero de piloto opcional
     * @return Datos de car data
     */
    public JsonNode getCarData(String sessionKey, Integer driverNumber) {
        return openF1Service.getCarData(sessionKey, driverNumber);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 27-04-2026
     * @description Obtiene posicion del coche en pista
     * @param sessionKey   Clave de sesion OpenF1
     * @param driverNumber Numero de piloto opcional
     * @return Datos de location
     */
    public JsonNode getLocation(String sessionKey, Integer driverNumber) {
        return openF1Service.getLocation(sessionKey, driverNumber);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 21-04-2026
     * @modified 03-05-2026
     * @description Construye una respuesta agregada para cargar el Live Center con
     *              una sola peticion
     * @param sessionKey Clave de sesion OpenF1
     * @return Datos live agrupados por seccion
     */
    public JsonNode getLiveOverview(String sessionKey) {
        ObjectNode response = objectMapper.createObjectNode();

        response.set("weather", openF1Service.getWeather(sessionKey));
        response.set("raceControl", openF1Service.getRaceControl(sessionKey));
        response.set("pitStops", openF1Service.getPitStops(sessionKey));
        response.set("stints", openF1Service.getStints(sessionKey));
        response.set("teamRadio", openF1Service.getTeamRadio(sessionKey));
        response.set("position", openF1Service.getPosition(sessionKey));
        response.set("intervals", openF1Service.getIntervals(sessionKey));
        response.set("laps", openF1Service.getLaps(sessionKey));
        response.set("overtakes", openF1Service.getOvertakes(sessionKey));
        response.set("startingGrid", openF1Service.getStartingGrid(sessionKey));
        response.set("championshipDrivers", openF1Service.getChampionshipDrivers(sessionKey));
        response.set("championshipTeams", openF1Service.getChampionshipTeams(sessionKey));
        response.set("carData", openF1Service.getCarData(sessionKey, null));
        response.set("location", openF1Service.getLocation(sessionKey, null));

        return response;
    }
}

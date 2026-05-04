/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.5
 * @created 17-04-2026
 * @modified 03-05-2026
 * @description Servicio para obtener datos de OpenF1 de forma tolerante a errores externos
 * @see https://openf1.org
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OpenF1Service {

    private static final int MAX_ATTEMPTS = 3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, ArrayNode> responseCache = new ConcurrentHashMap<>();

    @Value("${openf1.api.base-url:https://api.openf1.org/v1}")
    private String openF1BaseUrl;

    public OpenF1Service(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 17-04-2026
     * @modified 27-04-2026
     * @description Obtener meetings
     * @param year
     * @return
     */
    public JsonNode getMeetings(Integer year) {
        return fetchArray("/meetings", "year", year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener meetings
     * @param meetingKey
     * @return
     */
    public JsonNode getMeeting(Integer meetingKey) {
        return fetchArray("/meetings", "meeting_key", meetingKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 17-04-2026
     * @modified 27-04-2026
     * @description Obtener sesiones por meeting
     * @param meetingKey
     * @return
     */
    public JsonNode getSessions(Integer meetingKey) {
        return fetchArray("/sessions", "meeting_key", meetingKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 30-04-2026
     * @description Obtener sesiones por anio
     * @param year Temporada
     * @return Sesiones
     */
    public JsonNode getSessionsByYear(Integer year) {
        return fetchArray("/sessions", "year", year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 17-04-2026
     * @modified 27-04-2026
     * @description Obtener pilotos por sesión
     * @param sessionKey
     * @return
     */
    public JsonNode getDrivers(Integer sessionKey) {
        return fetchArray("/drivers", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 17-04-2026
     * @modified 27-04-2026
     * @description Obtener resultados de la sesión
     * @param sessionKey
     * @return
     */
    public JsonNode getSessionResults(Integer sessionKey) {
        return fetchArray("/session_result", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener tiempo
     * @param sessionKey
     * @return
     */
    public JsonNode getWeather(String sessionKey) {
        return fetchArray("/weather", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener race control
     * @param sessionKey
     * @return
     */
    public JsonNode getRaceControl(String sessionKey) {
        return fetchArray("/race_control", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener pit stops
     * @param sessionKey
     * @return
     */
    public JsonNode getPitStops(String sessionKey) {
        return fetchArray("/pit", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener stints
     * @param sessionKey
     * @return
     */
    public JsonNode getStints(String sessionKey) {
        return fetchArray("/stints", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener team radio
     * @param sessionKey
     * @return
     */
    public JsonNode getTeamRadio(String sessionKey) {
        return fetchArray("/team_radio", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener posición
     * @param sessionKey
     * @return
     */
    public JsonNode getPosition(String sessionKey) {
        return fetchArray("/position", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener intervalos
     * @param sessionKey
     * @return
     */
    public JsonNode getIntervals(String sessionKey) {
        return fetchArray("/intervals", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener vueltas
     * @param sessionKey
     * @return
     */
    public JsonNode getLaps(String sessionKey) {
        return fetchArray("/laps", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener adelantamientos
     * @param sessionKey
     * @return
     */
    public JsonNode getOvertakes(String sessionKey) {
        return fetchArray("/overtakes", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener parrilla de salida
     * @param sessionKey
     * @return
     */
    public JsonNode getStartingGrid(String sessionKey) {
        return fetchArray("/starting_grid", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener clasificación de pilotos
     * @param sessionKey
     * @return
     */
    public JsonNode getChampionshipDrivers(String sessionKey) {
        return fetchArray("/championship_drivers", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener clasificación de escuderías
     * @param sessionKey
     * @return
     */
    public JsonNode getChampionshipTeams(String sessionKey) {
        return fetchArray("/championship_teams", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener datos del coche
     * @param sessionKey
     * @param driverNumber
     * @return
     */
    public JsonNode getCarData(String sessionKey, Integer driverNumber) {
        return fetchArray(endpoint("/car_data").queryParam("session_key", sessionKey), "driver_number", driverNumber);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener posición del coche
     * @param sessionKey
     * @param driverNumber
     * @return
     */
    public JsonNode getLocation(String sessionKey, Integer driverNumber) {
        return fetchArray(endpoint("/location").queryParam("session_key", sessionKey), "driver_number", driverNumber);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Crea el constructor de URL para un endpoint OpenF1
     * @param path Ruta del endpoint
     * @return Constructor de URL
     */
    private UriComponentsBuilder endpoint(String path) {
        return UriComponentsBuilder.fromHttpUrl(openF1BaseUrl + path);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Construye una llamada OpenF1 con un parametro principal
     * @param path Endpoint OpenF1
     * @param param Parametro
     * @param value Valor
     * @return Array JSON
     */
    private ArrayNode fetchArray(String path, String param, Object value) {
        return fetchArray(endpoint(path), param, value);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Completa una URL OpenF1 con parametro opcional y ejecuta la llamada
     * @param builder Constructor de URL
     * @param param Parametro opcional
     * @param value Valor opcional
     * @return Array JSON
     */
    private ArrayNode fetchArray(UriComponentsBuilder builder, String param, Object value) {
        if (value != null) {
            builder.queryParam(param, value);
        }
        return fetchArray(builder.toUriString());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 21-04-2026
     * @modified 30-04-2026
     * @description Obtener array JSON tolerando caidas externas y rate limit de OpenF1
     * @param url URL completa de OpenF1
     * @return Array JSON
     */
    private ArrayNode fetchArray(String url) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                JsonNode response = restTemplate.getForObject(url, JsonNode.class);
                if (response != null && response.isArray()) {
                    ArrayNode result = (ArrayNode) response;
                    if (!result.isEmpty()) {
                        responseCache.put(url, result.deepCopy());
                    }
                    return result;
                }
            } catch (HttpClientErrorException.TooManyRequests ex) {
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt + 2);
                }
            } catch (HttpClientErrorException.NotFound ex) {
                return objectMapper.createArrayNode();
            } catch (RestClientException ex) {
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            }
        }

        ArrayNode cached = responseCache.get(url);
        return cached == null ? objectMapper.createArrayNode() : cached.deepCopy();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 03-05-2026
     * @description Espera entre reintentos externos respetando limites de API
     * @param attempt Intento actual
     */
    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(350L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}

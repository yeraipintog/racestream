/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.2
 * @created 17-04-2026
 * @modified 28-04-2026
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

@Service
public class OpenF1Service {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/meetings")
                .queryParam("year", year)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/meetings")
                .queryParam("meeting_key", meetingKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/sessions")
                .queryParam("meeting_key", meetingKey)
                .toUriString();

        return fetchArray(url);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener sesiones por año
     * @param year
     * @return
     */
    public JsonNode getSessionsByYear(Integer year) {
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/sessions")
                .queryParam("year", year)
                .toUriString();

        return fetchArray(url);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener sesión
     * @param sessionKey
     * @return
     */
    public JsonNode getSession(Integer sessionKey) {
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/sessions")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/drivers")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/session_result")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/weather")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/race_control")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/pit")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/stints")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/team_radio")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/position")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/intervals")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/laps")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/overtakes")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/starting_grid")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/championship_drivers")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/championship_teams")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return fetchArray(url);
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
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/car_data")
                .queryParam("session_key", sessionKey);

        if (driverNumber != null) {
            builder.queryParam("driver_number", driverNumber);
        }

        return fetchArray(builder.toUriString());
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
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/location")
                .queryParam("session_key", sessionKey);

        if (driverNumber != null) {
            builder.queryParam("driver_number", driverNumber);
        }

        return fetchArray(builder.toUriString());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 28-04-2026
     * @description Obtener array JSON evitando que una caida externa rompa los endpoints internos
     * @param url URL completa de OpenF1
     * @return Array JSON
     */
    private ArrayNode fetchArray(String url) {
        try {
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response != null && response.isArray()) {
                return (ArrayNode) response;
            }

            return objectMapper.createArrayNode();
        } catch (HttpClientErrorException.NotFound ex) {
            return objectMapper.createArrayNode();
        } catch (RestClientException ex) {
            return objectMapper.createArrayNode();
        }
    }
}

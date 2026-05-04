/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.4
 * @created 28-04-2026
 * @modified 03-05-2026
 * @description Servicio para consultar Jolpica F1 con cache por temporada, calendario y clasificaciones
 * @see https://github.com/jolpica/jolpica-f1
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JolpicaService {

    private static final int MAX_ATTEMPTS = 3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<Integer, ArrayNode> racesCache = new ConcurrentHashMap<>();
    private final Map<Integer, ArrayNode> driverStandingsCache = new ConcurrentHashMap<>();
    private final Map<Integer, ArrayNode> constructorStandingsCache = new ConcurrentHashMap<>();

    @Value("${jolpica.api.base-url:https://api.jolpi.ca/ergast/f1}")
    private String jolpicaBaseUrl;

    public JolpicaService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 28-04-2026
     * @modified 28-04-2026
     * @description Obtiene las carreras de una temporada desde Jolpica y reutiliza cache sin APIs deprecadas
     * @param year Temporada
     * @return Lista de carreras
     */
    public ArrayNode getRacesByYear(Integer year) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        ArrayNode cachedRaces = racesCache.get(selectedYear);
        if (cachedRaces != null) {
            return cachedRaces.deepCopy();
        }

        String url = UriComponentsBuilder
                .fromUriString(jolpicaBaseUrl)
                .pathSegment(String.valueOf(selectedYear), "races.json")
                .queryParam("limit", 100)
                .toUriString();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                JsonNode apiResponse = restTemplate.getForObject(url, JsonNode.class);
                JsonNode races = apiResponse == null
                        ? null
                        : apiResponse.path("MRData").path("RaceTable").path("Races");

                ArrayNode result = races != null && races.isArray()
                        ? (ArrayNode) races
                        : objectMapper.createArrayNode();
                if (!result.isEmpty()) {
                    racesCache.put(selectedYear, result.deepCopy());
                }
                return result.deepCopy();
            } catch (RestClientException ex) {
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            }
        }

        return objectMapper.createArrayNode();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Obtiene la clasificacion de pilotos desde Jolpica
     * @param year Temporada
     * @return Clasificacion de pilotos
     */
    public ArrayNode getDriverStandingsByYear(Integer year) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        ArrayNode cachedStandings = driverStandingsCache.get(selectedYear);
        if (cachedStandings != null) {
            return cachedStandings.deepCopy();
        }

        ArrayNode standings = getStandings(selectedYear, "driverstandings.json", "DriverStandings");
        driverStandingsCache.put(selectedYear, standings.deepCopy());
        return standings;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Obtiene la clasificacion de constructores desde Jolpica
     * @param year Temporada
     * @return Clasificacion de constructores
     */
    public ArrayNode getConstructorStandingsByYear(Integer year) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        ArrayNode cachedStandings = constructorStandingsCache.get(selectedYear);
        if (cachedStandings != null) {
            return cachedStandings.deepCopy();
        }

        ArrayNode standings = getStandings(selectedYear, "constructorstandings.json", "ConstructorStandings");
        constructorStandingsCache.put(selectedYear, standings.deepCopy());
        return standings;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Consulta una tabla de clasificacion Jolpica y devuelve sus filas
     * @param year Temporada
     * @param resource Recurso Jolpica
     * @param nodeName Nodo de clasificacion
     * @return Filas de clasificacion
     */
    private ArrayNode getStandings(Integer year, String resource, String nodeName) {
        String url = UriComponentsBuilder
                .fromUriString(jolpicaBaseUrl)
                .pathSegment(String.valueOf(year), resource)
                .queryParam("limit", 100)
                .toUriString();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                JsonNode apiResponse = restTemplate.getForObject(url, JsonNode.class);
                JsonNode standings = apiResponse == null
                        ? null
                        : apiResponse.path("MRData").path("StandingsTable").path("StandingsLists").path(0).path(nodeName);

                return standings != null && standings.isArray()
                        ? (ArrayNode) standings
                        : objectMapper.createArrayNode();
            } catch (RestClientException ex) {
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            }
        }

        return objectMapper.createArrayNode();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Espera breve entre reintentos externos
     * @param attempt Intento actual
     */
    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(160L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}

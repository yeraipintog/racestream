/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.6
 * @created 28-04-2026
 * @modified 04-05-2026
 * @description Servicio para consultar Jolpica F1 con cache por temporada, calendario, clasificaciones y resultados
 * @see https://github.com/jolpica/jolpica-f1
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.LinkedHashMap;
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
    private final Map<Integer, ArrayNode> raceResultsCache = new ConcurrentHashMap<>();

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
     * @created 04-05-2026
     * @description Devuelve todas las temporadas disponibles de Formula 1 en orden descendente
     * @return Temporadas disponibles
     */
    public ArrayNode getAvailableSeasons() {
        ArrayNode seasons = objectMapper.createArrayNode();
        for (int year = LocalDate.now().getYear(); year >= 1950; year--) {
            seasons.addObject().put("season", year);
        }
        return seasons;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 04-05-2026
     * @modified 04-05-2026
     * @description Obtiene todos los resultados paginados de carrera para detalles de pilotos y escuderias
     * @param year Temporada
     * @return Carreras con resultados oficiales
     */
    public ArrayNode getRaceResultsByYear(Integer year) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        ArrayNode cachedResults = raceResultsCache.get(selectedYear);
        if (cachedResults != null) {
            return cachedResults.deepCopy();
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ArrayNode result = getPagedRaceResults(selectedYear);
                if (!result.isEmpty()) {
                    raceResultsCache.put(selectedYear, result.deepCopy());
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
     * @created 04-05-2026
     * @description Recorre la paginacion de Jolpica y agrupa resultados por carrera
     * @param selectedYear Temporada
     * @return Carreras completas con sus resultados
     */
    private ArrayNode getPagedRaceResults(Integer selectedYear) {
        final int pageSize = 100;
        int offset = 0;
        int total = Integer.MAX_VALUE;
        Map<String, ObjectNode> racesByRound = new LinkedHashMap<>();

        while (offset < total) {
            String url = UriComponentsBuilder
                    .fromUriString(jolpicaBaseUrl)
                    .pathSegment(String.valueOf(selectedYear), "results.json")
                    .queryParam("limit", pageSize)
                    .queryParam("offset", offset)
                    .toUriString();
            JsonNode apiResponse = restTemplate.getForObject(url, JsonNode.class);
            JsonNode mrData = apiResponse == null ? null : apiResponse.path("MRData");
            JsonNode races = mrData == null ? null : mrData.path("RaceTable").path("Races");

            if (mrData != null) {
                total = parseInteger(mrData.path("total").asText(), offset);
            }
            if (races == null || !races.isArray() || races.isEmpty()) {
                break;
            }
            mergeRaceResults(racesByRound, (ArrayNode) races);
            offset += pageSize;
        }

        ArrayNode result = objectMapper.createArrayNode();
        racesByRound.values().forEach(result::add);
        return result;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Fusiona carreras repetidas si la paginacion parte sus resultados
     * @param racesByRound Carreras acumuladas
     * @param pageRaces Carreras de la pagina actual
     */
    private void mergeRaceResults(Map<String, ObjectNode> racesByRound, ArrayNode pageRaces) {
        for (JsonNode race : pageRaces) {
            String round = race.path("round").asText();
            ObjectNode storedRace = racesByRound.computeIfAbsent(round, key -> {
                ObjectNode copy = race.isObject() ? ((ObjectNode) race).deepCopy() : objectMapper.createObjectNode();
                copy.set("Results", objectMapper.createArrayNode());
                return copy;
            });
            ArrayNode storedResults = (ArrayNode) storedRace.path("Results");
            JsonNode pageResults = race.path("Results");
            if (pageResults.isArray()) {
                pageResults.forEach((result) -> storedResults.add(result.deepCopy()));
            }
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Convierte texto numerico de Jolpica sin romper la carga si viene vacio
     * @param value Valor original
     * @param fallback Valor por defecto
     * @return Numero convertido
     */
    private int parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
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

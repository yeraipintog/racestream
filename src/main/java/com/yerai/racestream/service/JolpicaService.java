/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.2
 * @created 28-04-2026
 * @modified 28-04-2026
 * @description Servicio para consultar Jolpica F1 con cache por temporada y URLs compatibles con Spring 6.2
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

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<Integer, ArrayNode> racesCache = new ConcurrentHashMap<>();

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

        try {
            JsonNode apiResponse = restTemplate.getForObject(url, JsonNode.class);
            JsonNode races = apiResponse == null
                    ? null
                    : apiResponse.path("MRData").path("RaceTable").path("Races");

            ArrayNode result = races != null && races.isArray()
                    ? (ArrayNode) races
                    : objectMapper.createArrayNode();
            racesCache.put(selectedYear, result.deepCopy());
            return result.deepCopy();
        } catch (RestClientException ex) {
            return objectMapper.createArrayNode();
        }
    }
}

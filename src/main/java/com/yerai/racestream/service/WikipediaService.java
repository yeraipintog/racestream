/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 28-04-2026
 * @description Servicio para obtener resumenes e imagenes de circuitos desde Wikipedia
 * @see https://api.wikimedia.org/wiki/Core_REST_API/Reference/Pages
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WikipediaService {

    private static final String SUMMARY_API = "https://en.wikipedia.org/api/rest_v1/page/summary/";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, ObjectNode> summaryCache = new ConcurrentHashMap<>();

    public WikipediaService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Obtiene imagen y descripcion de una pagina de Wikipedia
     * @param wikipediaUrl URL de Wikipedia
     * @return Resumen de Wikipedia
     */
    public ObjectNode getPageSummary(String wikipediaUrl) {
        String title = getTitleFromUrl(wikipediaUrl);

        if (title == null || title.isBlank()) {
            return objectMapper.createObjectNode();
        }

        if (summaryCache.containsKey(title)) {
            return summaryCache.get(title).deepCopy();
        }

        try {
            URI uri = URI.create(SUMMARY_API + UriUtils.encodePathSegment(title, StandardCharsets.UTF_8));
            RequestEntity<Void> request = RequestEntity
                    .get(uri)
                    .header(HttpHeaders.USER_AGENT, "RaceStream-TFG/1.0")
                    .build();
            ResponseEntity<JsonNode> response = restTemplate.exchange(request, JsonNode.class);
            JsonNode body = response.getBody();

            ObjectNode summary = body != null && body.isObject()
                    ? ((ObjectNode) body).deepCopy()
                    : objectMapper.createObjectNode();
            summaryCache.put(title, summary);
            return summary.deepCopy();
        } catch (IllegalArgumentException | RestClientException ex) {
            return objectMapper.createObjectNode();
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Extrae el titulo de una URL de Wikipedia
     * @param wikipediaUrl URL
     * @return Titulo de pagina
     */
    private String getTitleFromUrl(String wikipediaUrl) {
        if (wikipediaUrl == null || wikipediaUrl.isBlank()) {
            return null;
        }

        int index = wikipediaUrl.lastIndexOf("/wiki/");
        String title = index >= 0 ? wikipediaUrl.substring(index + 6) : wikipediaUrl;
        int queryIndex = title.indexOf('?');

        return queryIndex >= 0 ? title.substring(0, queryIndex) : title;
    }
}

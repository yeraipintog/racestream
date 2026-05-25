/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.3
 * @created 30-04-2026
 * @modified 03-05-2026
 * @description Servicio de recursos visuales de Fórmula 1 obtenidos desde APIs oficiales disponibles con fallbacks conocidos
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class F1MediaService {

    private static final int MAX_ATTEMPTS = 3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<Integer, ObjectNode> driverImageCache = new ConcurrentHashMap<>();

    public F1MediaService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 30-04-2026
     * @modified 03-05-2026
     * @description Devuelve la imagen de un piloto desde OpenF1 con caché, rate limit y fallback visual
     * @param driverNumber Número permanente del piloto
     * @return Datos visuales del piloto
     */
    public JsonNode getDriverImage(Integer driverNumber) {
        ObjectNode fallback = buildKnownDriverFallback(driverNumber);
        if (driverNumber == null) {
            return fallback;
        }
        ObjectNode cached = driverImageCache.get(driverNumber);
        if (cached != null) {
            return cached.deepCopy();
        }

        String url = UriComponentsBuilder
                .fromUriString("https://api.openf1.org/v1/drivers")
                .queryParam("driver_number", driverNumber)
                .toUriString();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                JsonNode drivers = restTemplate.getForObject(url, JsonNode.class);
                if (drivers == null || !drivers.isArray() || drivers.isEmpty()) {
                    return fallback;
                }

                JsonNode driver = findDriverWithHeadshot(drivers);
                if (driver == null) {
                    return fallback;
                }

                ObjectNode response = objectMapper.createObjectNode();
                response.put("headshotUrl", text(driver, "headshot_url"));
                response.put("fullName", text(driver, "full_name"));
                response.put("teamName", text(driver, "team_name"));
                driverImageCache.put(driverNumber, response.deepCopy());
                return response;
            } catch (HttpClientErrorException.TooManyRequests ex) {
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt + 2);
                }
            } catch (RestClientException ex) {
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            }
        }

        return fallback;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Devuelve imagen oficial conocida cuando OpenF1 todavía no ofrece headshot
     * @param driverNumber Número permanente del piloto
     * @return Fallback visual conocido o nodo vacío
     */
    private ObjectNode buildKnownDriverFallback(Integer driverNumber) {
        ObjectNode fallback = objectMapper.createObjectNode();
        if (Integer.valueOf(41).equals(driverNumber)) {
            fallback.put("headshotUrl", "https://media.formula1.com/image/upload/c_fill,w_96/q_auto/v1740000001/common/f1/2026/racingbulls/arvlin01/2026racingbullsarvlin01right.webp");
            fallback.put("fullName", "Arvid Lindblad");
            fallback.put("teamName", "Racing Bulls");
        }
        return fallback;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Busca el registro más reciente que tenga foto para evitar placeholders de pilotos
     * @param drivers Registros de OpenF1
     * @return Piloto con foto o null
     */
    private JsonNode findDriverWithHeadshot(JsonNode drivers) {
        for (int index = drivers.size() - 1; index >= 0; index--) {
            JsonNode driver = drivers.get(index);
            if (!text(driver, "headshot_url").isBlank()) {
                return driver;
            }
        }
        return null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Lee texto seguro de un nodo JSON
     * @param node Nodo
     * @param field Campo
     * @return Texto o vacío
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Espera entre reintentos externos para no superar el límite de OpenF1
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

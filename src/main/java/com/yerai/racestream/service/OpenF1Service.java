/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.0
 * @created 17-04-2026
 * @modified 23-05-2026
 * @description Servicio para obtener datos de OpenF1 con autenticación segura,
 *              rate limit, caché por endpoint y tolerancia a respuestas parciales
 * @see https://openf1.org
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class OpenF1Service {

    private static final Logger log = LoggerFactory.getLogger(OpenF1Service.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_REQUESTS_PER_SECOND = 5;
    private static final int MAX_REQUESTS_PER_MINUTE = 55;
    private static final String TOKEN_URL = "https://api.openf1.org/token";
    private static final long REFRESH_BEFORE_EXPIRY_SECONDS = 60L;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String openF1BaseUrl;
    private final String propertyUsername;
    private final String propertyPassword;
    private final String propertyAccessToken;
    private final Map<String, CacheEntry> responseCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> staleResponses = new ConcurrentHashMap<>();
    private final Map<String, EndpointStatus> endpointStatuses = new ConcurrentHashMap<>();
    private final Object rateLimitLock = new Object();
    private final Deque<Long> secondWindow = new ArrayDeque<>();
    private final Deque<Long> minuteWindow = new ArrayDeque<>();
    private volatile String accessToken = "";
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;
    private final ScheduledExecutorService tokenRefreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "openf1-token-refresher");
        thread.setDaemon(true);
        return thread;
    });
    private volatile ScheduledFuture<?> scheduledRefresh;

    public OpenF1Service(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${openf1.api.base-url:https://api.openf1.org/v1}") String openF1BaseUrl,
            @Value("${openf1.auth.username:}") String propertyUsername,
            @Value("${openf1.auth.password:}") String propertyPassword,
            @Value("${openf1.auth.access-token:}") String propertyAccessToken) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.openF1BaseUrl = clean(openF1BaseUrl);
        this.propertyUsername = clean(propertyUsername);
        this.propertyPassword = clean(propertyPassword);
        this.propertyAccessToken = clean(propertyAccessToken);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @description Prepara el token en segundo plano al arrancar sin bloquear
     *              indefinidamente la aplicación
     */
    @PostConstruct
    public void prepareTokenOnStartup() {
        tokenRefreshExecutor.execute(() -> {
            try {
                resolveAccessToken();
            } catch (RuntimeException ex) {
                log.warn("No se pudo preparar el token de OpenF1 al arrancar");
            }
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 17-04-2026
     * @modified 23-05-2026
     * @description Obtener meetings
     * @param year Temporada
     * @return Meetings
     */
    public JsonNode getMeetings(Integer year) {
        return fetchArray("/meetings", "year", year == null ? LocalDate.now().getYear() : year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 23-05-2026
     * @description Obtener meeting por clave
     * @param meetingKey Clave OpenF1
     * @return Meeting
     */
    public JsonNode getMeeting(Integer meetingKey) {
        return fetchArray("/meetings", "meeting_key", meetingKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 17-04-2026
     * @modified 23-05-2026
     * @description Obtener sesiones por meeting
     * @param meetingKey Clave OpenF1
     * @return Sesiones
     */
    public JsonNode getSessions(Integer meetingKey) {
        return fetchArray("/sessions", "meeting_key", meetingKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 21-04-2026
     * @modified 23-05-2026
     * @description Obtener sesiones por año
     * @param year Temporada
     * @return Sesiones
     */
    public JsonNode getSessionsByYear(Integer year) {
        return fetchArray("/sessions", "year", year == null ? LocalDate.now().getYear() : year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @description Obtener sesión por clave numérica o latest
     * @param sessionKey Clave OpenF1
     * @return Sesión
     */
    public JsonNode getSession(String sessionKey) {
        String key = clean(sessionKey);
        return key.isBlank() ? objectMapper.createArrayNode() : fetchArray("/sessions", "session_key", key);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 17-04-2026
     * @modified 23-05-2026
     * @description Obtener pilotos por sesión numérica
     * @param sessionKey Clave OpenF1
     * @return Pilotos
     */
    public JsonNode getDrivers(Integer sessionKey) {
        return fetchArray("/drivers", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 22-05-2026
     * @modified 23-05-2026
     * @description Obtener pilotos por clave de sesión numérica o latest
     * @param sessionKey Clave OpenF1
     * @return Pilotos
     */
    public JsonNode getDrivers(String sessionKey) {
        String key = clean(sessionKey);
        return key.isBlank() ? objectMapper.createArrayNode() : fetchArray("/drivers", "session_key", key);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 17-04-2026
     * @modified 23-05-2026
     * @description Obtener resultados de la sesión
     * @param sessionKey Clave OpenF1
     * @return Resultados
     */
    public JsonNode getSessionResults(Integer sessionKey) {
        return fetchArray("/session_result", "session_key", sessionKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @description Obtener resultados de la sesión por clave numérica o latest
     * @param sessionKey Clave OpenF1
     * @return Resultados
     */
    public JsonNode getSessionResults(String sessionKey) {
        String key = clean(sessionKey);
        return key.isBlank() ? objectMapper.createArrayNode() : fetchArray("/session_result", "session_key", key);
    }

    public JsonNode getWeather(String sessionKey) {
        return fetchArray("/weather", "session_key", clean(sessionKey));
    }

    public JsonNode getRaceControl(String sessionKey) {
        return fetchArray("/race_control", "session_key", clean(sessionKey));
    }

    public JsonNode getPitStops(String sessionKey) {
        return fetchArray("/pit", "session_key", clean(sessionKey));
    }

    public JsonNode getStints(String sessionKey) {
        return fetchArray("/stints", "session_key", clean(sessionKey));
    }

    public JsonNode getTeamRadio(String sessionKey) {
        return fetchArray("/team_radio", "session_key", clean(sessionKey));
    }

    public JsonNode getPosition(String sessionKey) {
        return fetchArray("/position", "session_key", clean(sessionKey));
    }

    public JsonNode getIntervals(String sessionKey) {
        return fetchArray("/intervals", "session_key", clean(sessionKey));
    }

    public JsonNode getLaps(String sessionKey) {
        return fetchArray("/laps", "session_key", clean(sessionKey));
    }

    public JsonNode getOvertakes(String sessionKey) {
        return fetchArray("/overtakes", "session_key", clean(sessionKey));
    }

    public JsonNode getStartingGrid(String sessionKey) {
        return fetchArray("/starting_grid", "session_key", clean(sessionKey));
    }

    public JsonNode getChampionshipDrivers(String sessionKey) {
        return fetchArray("/championship_drivers", "session_key", clean(sessionKey));
    }

    public JsonNode getChampionshipTeams(String sessionKey) {
        return fetchArray("/championship_teams", "session_key", clean(sessionKey));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 23-05-2026
     * @description Obtener datos recientes del coche, siempre con filtro temporal
     * @param sessionKey Clave OpenF1
     * @param driverNumber Número de piloto opcional
     * @return Datos del coche
     */
    public JsonNode getCarData(String sessionKey, Integer driverNumber) {
        return getCarData(sessionKey, driverNumber, 120);
    }

    public JsonNode getCarData(String sessionKey, Integer driverNumber, int lookbackSeconds) {
        UriComponentsBuilder builder = endpoint("/car_data")
                .queryParam("session_key", clean(sessionKey))
                .queryParam("date>", temporalLowerBound(Math.max(10, lookbackSeconds), 2));
        return fetchArray(builder, "driver_number", driverNumber);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 23-05-2026
     * @description Obtener ubicación reciente, siempre con filtro temporal
     * @param sessionKey Clave OpenF1
     * @param driverNumber Número de piloto opcional
     * @return Ubicación en pista
     */
    public JsonNode getLocation(String sessionKey, Integer driverNumber) {
        return getLocation(sessionKey, driverNumber, 120);
    }

    public JsonNode getLocation(String sessionKey, Integer driverNumber, int lookbackSeconds) {
        UriComponentsBuilder builder = endpoint("/location")
                .queryParam("session_key", clean(sessionKey))
                .queryParam("date>", temporalLowerBound(Math.max(10, lookbackSeconds), 2));
        return fetchArray(builder, "driver_number", driverNumber);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @description Devuelve el último registro de telemetría por piloto
     * @param sessionKey Clave OpenF1
     * @return Última telemetría por piloto
     */
    public ArrayNode getLatestCarDataByDriver(String sessionKey) {
        return latestByDriver(getCarData(sessionKey, null));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @description Devuelve la última ubicación por piloto
     * @param sessionKey Clave OpenF1
     * @return Última ubicación por piloto
     */
    public ArrayNode getLatestLocationByDriver(String sessionKey) {
        return latestByDriver(getLocation(sessionKey, null));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @description Comprueba si la última respuesta de una URL concreta llegó desde
     *              caché antigua por fallo, 429 o vacío temporal
     * @param url URL consultada
     * @return true si la respuesta fue stale
     */
    public boolean wasLastResponseStale(String url) {
        return Boolean.TRUE.equals(staleResponses.get(url));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @modified 23-05-2026
     * @description Comprueba si el endpoint y parámetros indicados han usado la
     *              última respuesta válida por fallo, rate limit o vacío temporal
     * @param path Endpoint OpenF1
     * @param params Parámetros públicos usados
     * @return true si la última respuesta conocida es stale
     */
    public boolean wasEndpointStale(String path, Map<String, Object> params) {
        return wasLastResponseStale(urlFor(path, params));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @modified 23-05-2026
     * @description Ejecuta una comprobación segura de un endpoint sin exponer
     *              token, usuario, contraseña ni cabeceras sensibles
     * @param path Endpoint OpenF1
     * @param params Parámetros públicos usados
     * @return Diagnóstico resumido
     */
    public ObjectNode diagnoseEndpoint(String path, Map<String, Object> params) {
        String url = urlFor(path, params);
        ArrayNode data = fetchArray(url);
        EndpointStatus status = endpointStatuses.getOrDefault(url, EndpointStatus.empty(Instant.now()));
        ObjectNode row = objectMapper.createObjectNode();
        row.put("endpoint", normalizePath(path));
        ObjectNode paramsNode = row.putObject("params");
        params.forEach((key, value) -> {
            if (value != null && !clean(String.valueOf(value)).isBlank()) {
                paramsNode.put(key, String.valueOf(value));
            }
        });
        row.put("statusHttp", status.httpStatus());
        row.put("status", status.status());
        row.put("records", data.size());
        row.put("lastUpdated", status.updatedAt().toString());
        row.put("fromCache", status.fromCache());
        row.put("stale", status.stale());
        row.put("fromLastValid", status.fromLastValid());
        row.put("message", status.message());
        if (!data.isEmpty() && data.get(0).isObject()) {
            row.set("example", reducedExample(data.get(0)));
        }
        return row;
    }

    private UriComponentsBuilder endpoint(String path) {
        return UriComponentsBuilder.fromHttpUrl(openF1BaseUrl + normalizePath(path));
    }

    private ArrayNode fetchArray(String path, String param, Object value) {
        return fetchArray(endpoint(path), param, value);
    }

    private ArrayNode fetchArray(UriComponentsBuilder builder, String param, Object value) {
        if (value != null && !clean(String.valueOf(value)).isBlank()) {
            builder.queryParam(param, value);
        }
        return fetchArray(builder.build(false).toUriString());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.2.0
     * @created 21-04-2026
     * @modified 23-05-2026
     * @description Obtener array JSON respetando rate limit, TTL por endpoint y
     *              última respuesta válida ante vacíos temporales
     * @param url URL completa de OpenF1
     * @return Array JSON seguro
     */
    private ArrayNode fetchArray(String url) {
        Duration ttl = ttlFor(url);
        CacheEntry cached = responseCache.get(url);
        if (cached != null && cached.isFresh()) {
            staleResponses.put(url, false);
            endpointStatuses.put(url, new EndpointStatus(200, "cached", cached.data().size(), true, false, false,
                    "Datos servidos desde caché válida.", cached.fetchedAt()));
            return cached.copy();
        }

        boolean receivedEmptyResponse = false;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                RemoteResponse response = fetchRemoteJson(url);
                if (response.body() != null && response.body().isArray()) {
                    ArrayNode result = (ArrayNode) response.body();
                    if (!result.isEmpty()) {
                        responseCache.put(url, new CacheEntry(result.deepCopy(), Instant.now(), ttl));
                        staleResponses.put(url, false);
                        endpointStatuses.put(url, new EndpointStatus(response.httpStatus(), "loaded", result.size(),
                                false, false, false, "Datos actualizados desde OpenF1.", Instant.now()));
                        return result.deepCopy();
                    }
                    receivedEmptyResponse = true;
                    endpointStatuses.put(url, new EndpointStatus(response.httpStatus(), "empty", 0, false, false,
                            false, "OpenF1 ha devuelto un array vacío para estos parámetros.", Instant.now()));
                    if (attempt < MAX_ATTEMPTS) {
                        sleepBeforeRetry(attempt);
                    }
                }
            } catch (HttpClientErrorException.TooManyRequests ex) {
                markError(url, ex.getStatusCode().value(), "rate_limited",
                        "OpenF1 ha aplicado rate limit. Se usa la última respuesta válida si existe.");
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt + 2);
                }
            } catch (HttpClientErrorException.NotFound ex) {
                return cachedOrEmpty(url, ex.getStatusCode().value(),
                        "Endpoint no disponible para esta sesión o parámetros.");
            } catch (HttpClientErrorException.Forbidden ex) {
                return cachedOrEmpty(url, ex.getStatusCode().value(),
                        "OpenF1 ha denegado el acceso al endpoint solicitado.");
            } catch (HttpClientErrorException ex) {
                return cachedOrEmpty(url, ex.getStatusCode().value(),
                        ex.getStatusCode().value() == 401
                                ? "No se pudo autenticar con OpenF1. Revisa las credenciales de application.properties."
                                : "OpenF1 ha devuelto un error controlado.");
            } catch (RestClientException ex) {
                markError(url, 0, "network_error", "No se pudo contactar con OpenF1.");
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            }
        }

        if (receivedEmptyResponse) {
            return cachedOrEmpty(url, 200, "OpenF1 ha devuelto vacío temporalmente.");
        }
        return cachedOrEmpty(url, 0, "Sin respuesta válida de OpenF1.");
    }

    private RemoteResponse fetchRemoteJson(String url) {
        awaitRateLimitSlot();
        try {
            return exchangeJson(url, resolveAccessToken());
        } catch (HttpClientErrorException.Unauthorized ex) {
            String renewedToken = forceRefreshAccessToken();
            if (!renewedToken.isBlank()) {
                awaitRateLimitSlot();
                return exchangeJson(url, renewedToken);
            }
            throw ex;
        }
    }

    private RemoteResponse exchangeJson(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (!token.isBlank()) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class);
        return new RemoteResponse(response.getBody(), response.getStatusCode().value());
    }

    private String resolveAccessToken() {
        Instant now = Instant.now();
        if (!accessToken.isBlank() && accessTokenExpiresAt.isAfter(now.plusSeconds(REFRESH_BEFORE_EXPIRY_SECONDS))) {
            return accessToken;
        }

        if (!hasCredentials()) {
            String configuredToken = clean(propertyAccessToken);
            if (!configuredToken.isBlank()) {
                accessToken = configuredToken;
                accessTokenExpiresAt = Instant.now().plus(Duration.ofMinutes(10));
            }
            return accessToken;
        }

        synchronized (this) {
            if (!accessToken.isBlank()
                    && accessTokenExpiresAt.isAfter(Instant.now().plusSeconds(REFRESH_BEFORE_EXPIRY_SECONDS))) {
                return accessToken;
            }
            String token = requestAccessToken(resolveUsername(), resolvePassword());
            if (!token.isBlank()) {
                return token;
            }
            String configuredToken = clean(propertyAccessToken);
            if (!configuredToken.isBlank()) {
                accessToken = configuredToken;
                accessTokenExpiresAt = Instant.now().plus(Duration.ofMinutes(10));
            }
            return accessToken;
        }
    }

    private String forceRefreshAccessToken() {
        if (!hasCredentials()) {
            return "";
        }
        synchronized (this) {
            accessToken = "";
            accessTokenExpiresAt = Instant.EPOCH;
            return requestAccessToken(resolveUsername(), resolvePassword());
        }
    }

    private String requestAccessToken(String username, String password) {
        if (clean(username).isBlank() || clean(password).isBlank()) {
            log.warn("Credenciales de OpenF1 ausentes en application.properties");
            return "";
        }
        try {
            awaitRateLimitSlot();
            String body = "username=" + encodeForm(username) + "&password=" + encodeForm(password);
            JsonNode response = restTemplate.execute(
                    TOKEN_URL,
                    HttpMethod.POST,
                    request -> {
                        request.getHeaders().setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                        request.getBody().write(body.getBytes(StandardCharsets.UTF_8));
                    },
                    clientHttpResponse -> objectMapper.readTree(clientHttpResponse.getBody()));
            String token = response == null ? "" : clean(response.path("access_token").asText(""));
            long expiresIn = response == null ? 0L : response.path("expires_in").asLong(3600L);
            if (!token.isBlank()) {
                accessToken = token;
                accessTokenExpiresAt = Instant.now().plusSeconds(Math.max(1L, expiresIn));
                scheduleTokenRefresh(expiresIn);
            }
            return token;
        } catch (RestClientException ex) {
            log.warn("Error solicitando token OpenF1");
            return "";
        }
    }

    private void scheduleTokenRefresh(long expiresInSeconds) {
        if (expiresInSeconds <= 0 || !hasCredentials()) {
            return;
        }
        try {
            if (scheduledRefresh != null && !scheduledRefresh.isDone()) {
                scheduledRefresh.cancel(false);
            }
            long delay = Math.max(5L, expiresInSeconds - REFRESH_BEFORE_EXPIRY_SECONDS);
            scheduledRefresh = tokenRefreshExecutor.schedule(() -> {
                try {
                    forceRefreshAccessToken();
                } catch (RuntimeException ex) {
                    log.warn("Error al renovar token OpenF1 programado");
                }
            }, delay, TimeUnit.SECONDS);
        } catch (RuntimeException ex) {
            log.warn("No se pudo programar la renovación del token OpenF1");
        }
    }

    private String resolveUsername() {
        return propertyUsername;
    }

    private String resolvePassword() {
        return propertyPassword;
    }

    private boolean hasCredentials() {
        return !resolveUsername().isBlank() && !resolvePassword().isBlank();
    }

    private ArrayNode latestByDriver(JsonNode items) {
        Map<Integer, JsonNode> rows = new LinkedHashMap<>();
        if (items != null && items.isArray()) {
            items.forEach(item -> {
                int driverNumber = item.path("driver_number").asInt(-1);
                if (driverNumber > 0) {
                    JsonNode current = rows.get(driverNumber);
                    if (current == null || safeInstant(item.path("date").asText(""))
                            .isAfter(safeInstant(current.path("date").asText("")))) {
                        rows.put(driverNumber, item);
                    }
                }
            });
        }
        ArrayNode result = objectMapper.createArrayNode();
        rows.values().stream()
                .sorted(Comparator.comparingInt(item -> item.path("driver_number").asInt(999)))
                .forEach(item -> result.add(item.deepCopy()));
        return result;
    }

    private ArrayNode cachedOrEmpty(String url, int httpStatus, String message) {
        CacheEntry cached = responseCache.get(url);
        staleResponses.put(url, cached != null);
        endpointStatuses.put(url, new EndpointStatus(httpStatus, cached == null ? "empty" : "stale",
                cached == null ? 0 : cached.data().size(), false, cached != null, cached != null, message,
                Instant.now()));
        return cached == null ? objectMapper.createArrayNode() : cached.copy();
    }

    private void markError(String url, int httpStatus, String status, String message) {
        CacheEntry cached = responseCache.get(url);
        staleResponses.put(url, cached != null);
        endpointStatuses.put(url, new EndpointStatus(httpStatus, status, cached == null ? 0 : cached.data().size(),
                false, cached != null, cached != null, message, Instant.now()));
    }

    private Duration ttlFor(String url) {
        String path = "";
        try {
            path = URI.create(url).getPath();
        } catch (IllegalArgumentException ex) {
            return Duration.ofSeconds(10);
        }
        if (path.endsWith("/car_data") || path.endsWith("/location")) {
            return Duration.ofSeconds(2);
        }
        if (path.endsWith("/position") || path.endsWith("/intervals") || path.endsWith("/laps")) {
            return Duration.ofSeconds(4);
        }
        if (path.endsWith("/race_control") || path.endsWith("/pit") || path.endsWith("/stints")
                || path.endsWith("/team_radio") || path.endsWith("/overtakes") || path.endsWith("/starting_grid")
                || path.endsWith("/session_result") || path.endsWith("/championship")
                || path.endsWith("/championship_drivers") || path.endsWith("/championship_teams")) {
            return Duration.ofSeconds(15);
        }
        if (path.endsWith("/weather")) {
            return Duration.ofSeconds(60);
        }
        if (path.endsWith("/sessions") || path.endsWith("/drivers") || path.endsWith("/meetings")) {
            return Duration.ofMinutes(12);
        }
        return Duration.ofSeconds(10);
    }

    private void awaitRateLimitSlot() {
        while (true) {
            long sleepMillis;
            synchronized (rateLimitLock) {
                long now = System.currentTimeMillis();
                trimWindow(secondWindow, now - 1000L);
                trimWindow(minuteWindow, now - 60000L);
                if (secondWindow.size() < MAX_REQUESTS_PER_SECOND && minuteWindow.size() < MAX_REQUESTS_PER_MINUTE) {
                    secondWindow.addLast(now);
                    minuteWindow.addLast(now);
                    return;
                }
                long secondWait = secondWindow.isEmpty() ? 50L : 1000L - (now - secondWindow.peekFirst()) + 5L;
                long minuteWait = minuteWindow.isEmpty() ? 50L : 60000L - (now - minuteWindow.peekFirst()) + 5L;
                sleepMillis = Math.max(50L, Math.min(secondWait, minuteWait));
            }
            sleep(sleepMillis);
        }
    }

    private void trimWindow(Deque<Long> window, long oldestAllowed) {
        Iterator<Long> iterator = window.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() >= oldestAllowed) {
                break;
            }
            iterator.remove();
        }
    }

    private void sleepBeforeRetry(int attempt) {
        sleep(Math.min(2000L, 350L * attempt));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String encodeForm(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String temporalLowerBound(int lookbackSeconds, int bucketSeconds) {
        long now = Instant.now().getEpochSecond();
        long bucket = (now / bucketSeconds) * bucketSeconds;
        return Instant.ofEpochSecond(bucket - lookbackSeconds).toString();
    }

    private String urlFor(String path, Map<String, Object> params) {
        UriComponentsBuilder builder = endpoint(path);
        params.forEach((key, value) -> {
            if (value != null && !clean(String.valueOf(value)).isBlank()) {
                builder.queryParam(key, value);
            }
        });
        return builder.build(false).toUriString();
    }

    private String normalizePath(String path) {
        String value = clean(path);
        return value.startsWith("/") ? value : "/" + value;
    }

    private ObjectNode reducedExample(JsonNode node) {
        ObjectNode result = objectMapper.createObjectNode();
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                return;
            }
            if (value.isNumber()) {
                result.set(entry.getKey(), value);
            } else if (value.isBoolean()) {
                result.put(entry.getKey(), value.asBoolean());
            } else {
                String text = value.asText("");
                result.put(entry.getKey(), text.length() > 80 ? text.substring(0, 80) + "..." : text);
            }
        });
        return result;
    }

    private Instant safeInstant(String value) {
        try {
            return clean(value).isBlank() ? Instant.EPOCH : Instant.parse(value);
        } catch (RuntimeException ex) {
            return Instant.EPOCH;
        }
    }

    @PreDestroy
    private void shutdownTokenRefresher() {
        try {
            if (scheduledRefresh != null) {
                scheduledRefresh.cancel(false);
            }
            tokenRefreshExecutor.shutdownNow();
        } catch (RuntimeException ex) {
            // Sin acción: el cierre de la aplicación no debe bloquearse.
        }
    }

    private record RemoteResponse(JsonNode body, int httpStatus) {
    }

    private record EndpointStatus(
            int httpStatus,
            String status,
            int records,
            boolean fromCache,
            boolean stale,
            boolean fromLastValid,
            String message,
            Instant updatedAt) {

        private static EndpointStatus empty(Instant updatedAt) {
            return new EndpointStatus(0, "pending", 0, false, false, false,
                    "Todavía no se ha consultado este endpoint.", updatedAt);
        }
    }

    private record CacheEntry(ArrayNode data, Instant fetchedAt, Duration ttl) {
        private boolean isFresh() {
            return Instant.now().isBefore(fetchedAt.plus(ttl));
        }

        private ArrayNode copy() {
            return data.deepCopy();
        }
    }
}

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.3.0
 * @created 23-05-2026
 * @modified 26-05-2026
 * @description Resuelve la sesión real de En Vivo sin depender solo del horario
 *              teórico, contemplando retrasos, latest, datos parciales y caché ligera
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LiveSessionResolver {

    private static final Duration FINISH_GRACE = Duration.ofHours(4);

    private final OpenF1Service openF1Service;
    private final ObjectMapper objectMapper;
    private final Map<String, DataPresenceCacheEntry> presenceCache = new ConcurrentHashMap<>();
    private volatile LiveSessionResolution lastResolved;

    public LiveSessionResolver(OpenF1Service openF1Service, ObjectMapper objectMapper) {
        this.openF1Service = openF1Service;
        this.objectMapper = objectMapper;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @modified 23-05-2026
     * @description Devuelve la sesión live más fiable disponible usando latest,
     *              continuidad local y margen de gracia tras date_end
     * @param requestedSessionKey Sesión solicitada, opcional
     * @return Resolución de sesión para En Vivo
     */
    public LiveSessionResolution resolve(String requestedSessionKey) {
        String requested = clean(requestedSessionKey);
        if (!requested.isBlank() && !"latest".equalsIgnoreCase(requested)) {
            LiveSessionResolution explicit = resolveExplicit(requested);
            lastResolved = explicit;
            return explicit;
        }

        ObjectNode latestSession = firstObject(openF1Service.getSession("latest"));
        DataPresence latestPresence = detectUsefulLiveData("latest");
        if (!latestSession.isEmpty() && latestPresence.useful()
                && !(latestPresence.finishedSignal() && !latestPresence.hasLiveSignal())) {
            LiveSessionResolution resolved = buildResolution(latestSession, "latest", latestPresence);
            lastResolved = resolved;
            return resolved;
        }

        if (!latestSession.isEmpty() && latestPresence.finishedSignal() && !latestPresence.hasLiveSignal()) {
            ObjectNode nextSession = resolveNextSessionAfter(latestSession);
            if (!nextSession.isEmpty()) {
                String key = keyFrom(nextSession, "");
                DataPresence presence = key.isBlank() ? DataPresence.empty() : detectUsefulLiveData(key);
                LiveSessionResolution resolved = buildResolution(nextSession, key, presence);
                lastResolved = resolved;
                return resolved;
            }
        }

        LiveSessionResolution continuity = keepPreviousIfStillUseful();
        if (continuity != null) {
            return continuity;
        }

        ObjectNode calendarSession = resolveCalendarSession(latestSession);
        if (!calendarSession.isEmpty()) {
            String key = keyFrom(calendarSession, "");
            DataPresence presence = key.isBlank() ? DataPresence.empty() : detectUsefulLiveData(key);
            LiveSessionResolution resolved = buildResolution(calendarSession, key, presence);
            lastResolved = resolved;
            return resolved;
        }

        LiveSessionResolution empty = new LiveSessionResolution(
                objectMapper.createObjectNode(),
                "",
                "Sin datos disponibles todavía",
                false);
        lastResolved = empty;
        return empty;
    }

    private LiveSessionResolution resolveExplicit(String requested) {
        ObjectNode session = firstObject(openF1Service.getSession(requested));
        return buildResolution(session, requested, detectUsefulLiveData(requested));
    }

    private LiveSessionResolution keepPreviousIfStillUseful() {
        LiveSessionResolution current = lastResolved;
        if (current == null || current.sessionKey().isBlank()) {
            return null;
        }
        DataPresence presence = detectUsefulLiveData(current.sessionKey());
        if (!presence.useful()) {
            return null;
        }
        LiveSessionResolution resolved = buildResolution(current.session(), current.sessionKey(), presence);
        lastResolved = resolved;
        return resolved;
    }

    private ObjectNode resolveCalendarSession(ObjectNode latestSession) {
        String latestMeetingKey = latestSession.path("meeting_key").asText("");
        JsonNode sessions = openF1Service.getSessionsByYear(LocalDate.now().getYear());
        List<ObjectNode> rows = new ArrayList<>();
        if (sessions != null && sessions.isArray()) {
            sessions.forEach(item -> {
                if (item != null && item.isObject() && !item.path("is_cancelled").asBoolean(false)) {
                    rows.add((ObjectNode) item.deepCopy());
                }
            });
        }
        rows.sort(Comparator.comparing(this::safeStart));

        Instant now = Instant.now();
        if (!latestMeetingKey.isBlank()) {
            ObjectNode meetingSession = rows.stream()
                    .filter(item -> latestMeetingKey.equals(item.path("meeting_key").asText("")))
                    .filter(item -> !safeStart(item).isAfter(now))
                    .max(Comparator.comparing(this::safeStart))
                    .orElse(null);
            if (meetingSession != null) {
                return meetingSession;
            }
        }

        ObjectNode graceCandidate = rows.stream()
                .filter(item -> !safeStart(item).isAfter(now))
                .filter(item -> safeEnd(item).plus(FINISH_GRACE).isAfter(now))
                .max(Comparator.comparing(this::safeStart))
                .orElse(null);
        if (graceCandidate != null) {
            return graceCandidate;
        }

        return rows.stream()
                .filter(item -> safeStart(item).isAfter(now))
                .min(Comparator.comparing(this::safeStart))
                .orElse(objectMapper.createObjectNode());
    }

    private ObjectNode resolveNextSessionAfter(ObjectNode latestSession) {
        String meetingKey = latestSession.path("meeting_key").asText("");
        Instant latestStart = safeStart(latestSession);
        Instant now = Instant.now();
        JsonNode sessions = openF1Service.getSessionsByYear(LocalDate.now().getYear());
        List<ObjectNode> rows = new ArrayList<>();
        if (sessions != null && sessions.isArray()) {
            sessions.forEach(item -> {
                if (item != null && item.isObject() && !item.path("is_cancelled").asBoolean(false)) {
                    rows.add((ObjectNode) item.deepCopy());
                }
            });
        }
        return rows.stream()
                .filter(item -> meetingKey.isBlank() || meetingKey.equals(item.path("meeting_key").asText("")))
                .filter(item -> safeStart(item).isAfter(latestStart))
                .filter(item -> safeEnd(item).plus(FINISH_GRACE).isAfter(now))
                .min(Comparator.comparing(this::safeStart))
                .orElse(objectMapper.createObjectNode());
    }

    private LiveSessionResolution buildResolution(ObjectNode session, String fallbackKey, DataPresence presence) {
        ObjectNode safeSession = session == null ? objectMapper.createObjectNode() : session.deepCopy();
        String key = keyFrom(safeSession, fallbackKey);
        return new LiveSessionResolution(safeSession, key, statusFor(safeSession, presence), presence.useful());
    }

    private String statusFor(ObjectNode session, DataPresence presence) {
        if (session == null || session.isEmpty()) {
            return "Sin datos disponibles todavía";
        }
        Instant now = Instant.now();
        Instant start = safeStart(session);
        Instant end = safeEnd(session);
        boolean hasDates = !Instant.EPOCH.equals(start) && !Instant.EPOCH.equals(end);
        if (!presence.useful()) {
            if (hasDates && now.isBefore(start)) {
                return "Sin datos disponibles todavía";
            }
            if (hasDates && now.isAfter(end.plus(FINISH_GRACE))) {
                return "Sesión finalizada";
            }
            return "Esperando datos";
        }
        if (presence.finishedSignal() && !presence.hasLiveSignal()) {
            return "Sesión finalizada";
        }
        if (hasDates && now.isBefore(start)) {
            return presence.hasLiveSignal() ? "Sesión retrasada" : "Esperando datos";
        }
        if (hasDates && !now.isBefore(start) && !now.isAfter(end)) {
            return presence.hasLiveSignal() ? "En directo" : "Esperando datos";
        }
        if (hasDates && now.isAfter(end) && !now.isAfter(end.plus(FINISH_GRACE))) {
            return presence.hasLiveSignal() ? "Sesión retrasada" : "Esperando datos";
        }
        if (hasDates && now.isAfter(end.plus(FINISH_GRACE))) {
            return presence.hasLiveSignal() ? "Sesión retrasada" : "Sesión finalizada";
        }
        return presence.hasLiveSignal() ? "En directo" : "Esperando datos";
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 23-05-2026
     * @modified 26-05-2026
     * @description Detecta si una sesión tiene datos útiles reutilizando una caché
     *              breve para no saturar OpenF1 con cada refresco de estado
     * @param sessionKey Clave de sesión OpenF1
     * @return Presencia de datos útil para resolver estado live
     */
    private DataPresence detectUsefulLiveData(String sessionKey) {
        String key = clean(sessionKey);
        if (key.isBlank()) {
            return DataPresence.empty();
        }

        DataPresenceCacheEntry cached = presenceCache.get(key);
        if (cached != null && cached.isFresh()) {
            return cached.presence();
        }

        DataPresence presence = loadDataPresence(key);
        presenceCache.put(key, new DataPresenceCacheEntry(
                presence,
                Instant.now(),
                Duration.ofSeconds(presence.hasLiveSignal() ? 2 : 8)));
        return presence;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 26-05-2026
     * @modified 26-05-2026
     * @description Consulta primero endpoints ligeros y solo pide telemetría si ya
     *              hay señales de sesión para reducir latencia y rate limit
     * @param sessionKey Clave de sesión OpenF1
     * @return Presencia de datos calculada
     */
    private DataPresence loadDataPresence(String sessionKey) {
        JsonNode drivers = openF1Service.getDrivers(sessionKey);
        JsonNode position = openF1Service.getPosition(sessionKey);
        JsonNode laps = openF1Service.getLaps(sessionKey);
        JsonNode raceControl = openF1Service.getRaceControl(sessionKey);
        JsonNode sessionResult = openF1Service.getSessionResults(sessionKey);
        JsonNode weather = openF1Service.getWeather(sessionKey);
        boolean hasBaseData = hasItems(drivers) || hasItems(position) || hasItems(laps)
                || hasItems(raceControl) || hasItems(sessionResult) || hasItems(weather);
        boolean finishedSignal = hasFinishSignal(raceControl);
        JsonNode carData = objectMapper.createArrayNode();
        JsonNode location = objectMapper.createArrayNode();
        if (hasBaseData && !finishedSignal) {
            carData = openF1Service.getLatestCarDataByDriver(sessionKey);
            location = openF1Service.getLatestLocationByDriver(sessionKey);
        }
        boolean hasSessionResult = hasItems(sessionResult);
        boolean freshTelemetry = hasFreshDatedItems(carData, 150) || hasFreshDatedItems(location, 150);
        return new DataPresence(
                hasItems(drivers),
                hasItems(position),
                hasItems(laps),
                hasItems(carData),
                hasItems(location),
                hasItems(raceControl),
                hasItems(weather),
                hasSessionResult,
                freshTelemetry,
                finishedSignal);
    }

    private boolean hasItems(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty();
    }

    private boolean hasFreshDatedItems(JsonNode node, long maxAgeSeconds) {
        if (node == null || !node.isArray()) {
            return false;
        }
        Instant threshold = Instant.now().minusSeconds(maxAgeSeconds);
        for (JsonNode item : node) {
            if (parseInstant(item.path("date").asText("")).isAfter(threshold)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFinishSignal(JsonNode node) {
        if (node == null || !node.isArray()) {
            return false;
        }
        for (JsonNode item : node) {
            String text = (item.path("message").asText("") + " "
                    + item.path("flag").asText("") + " "
                    + item.path("category").asText("") + " "
                    + item.path("status").asText("")).toLowerCase();
            if (text.contains("chequered") || text.contains("session finished")
                    || text.contains("session end") || text.contains("finalizada")) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode firstObject(JsonNode node) {
        if (node != null && node.isArray() && !node.isEmpty() && node.get(0).isObject()) {
            return (ObjectNode) node.get(0).deepCopy();
        }
        return objectMapper.createObjectNode();
    }

    private String keyFrom(ObjectNode session, String fallbackKey) {
        String key = session == null ? "" : session.path("session_key").asText("");
        return key.isBlank() ? clean(fallbackKey) : key;
    }

    private Instant safeStart(ObjectNode session) {
        return parseInstant(session == null ? "" : session.path("date_start").asText(""));
    }

    private Instant safeEnd(ObjectNode session) {
        Instant end = parseInstant(session == null ? "" : session.path("date_end").asText(""));
        if (!Instant.EPOCH.equals(end)) {
            return end;
        }
        Instant start = safeStart(session);
        return Instant.EPOCH.equals(start) ? Instant.EPOCH : start.plus(Duration.ofHours(2));
    }

    private Instant parseInstant(String value) {
        try {
            return clean(value).isBlank() ? Instant.EPOCH : Instant.parse(value);
        } catch (RuntimeException ex) {
            return Instant.EPOCH;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record LiveSessionResolution(ObjectNode session, String sessionKey, String status, boolean usefulData) {
    }

    private record DataPresence(
            boolean drivers,
            boolean position,
            boolean laps,
            boolean carData,
            boolean location,
            boolean raceControl,
            boolean weather,
            boolean sessionResult,
            boolean freshTelemetry,
            boolean finishedSignal) {

        private boolean useful() {
            return drivers || position || laps || carData || location || raceControl || weather || sessionResult;
        }

        private boolean hasLiveSignal() {
            return freshTelemetry;
        }

        private static DataPresence empty() {
            return new DataPresence(false, false, false, false, false, false, false, false, false, false);
        }
    }

    private record DataPresenceCacheEntry(DataPresence presence, Instant fetchedAt, Duration ttl) {
        private boolean isFresh() {
            return Instant.now().isBefore(fetchedAt.plus(ttl));
        }
    }
}

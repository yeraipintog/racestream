/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.4.1
 * @created 21-04-2026
 * @modified 26-05-2026
 * @description Servicio del Live Center que construye bloques ligeros,
 *              cacheados, fusionados con streaming y tolerantes a sesiones
 *              retrasadas de OpenF1
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class F1LiveService {

    private static final String DASH = "—";
    private final OpenF1Service openF1Service;
    private final LiveSessionResolver liveSessionResolver;
    private final F1ScheduleService f1ScheduleService;
    private final LiveDataStreamState liveDataStreamState;
    private final ObjectMapper objectMapper;
    private final Map<String, BlockCacheEntry> blockCache = new ConcurrentHashMap<>();

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.3.0
     * @created 21-04-2026
     * @modified 24-05-2026
     * @description Constructor con dependencias necesarias para consultar OpenF1,
     *              resolver la sesión live y construir respuestas JSON
     * @param openF1Service Servicio base de OpenF1
     * @param liveSessionResolver Resolvedor central de sesiones live
     * @param f1ScheduleService Servicio de calendario enriquecido
     * @param liveDataStreamState Estado MQTT recibido en backend
     * @param objectMapper Mapper para construir respuestas agregadas
     */
    public F1LiveService(
            OpenF1Service openF1Service,
            LiveSessionResolver liveSessionResolver,
            F1ScheduleService f1ScheduleService,
            LiveDataStreamState liveDataStreamState,
            ObjectMapper objectMapper) {
        this.openF1Service = openF1Service;
        this.liveSessionResolver = liveSessionResolver;
        this.f1ScheduleService = f1ScheduleService;
        this.liveDataStreamState = liveDataStreamState;
        this.objectMapper = objectMapper;
    }

    public JsonNode getWeather(String sessionKey) {
        return openF1Service.getWeather(sessionKey);
    }

    public JsonNode getRaceControl(String sessionKey) {
        return openF1Service.getRaceControl(sessionKey);
    }

    public JsonNode getPitStops(String sessionKey) {
        return openF1Service.getPitStops(sessionKey);
    }

    public JsonNode getStints(String sessionKey) {
        return openF1Service.getStints(sessionKey);
    }

    public JsonNode getTeamRadio(String sessionKey) {
        return openF1Service.getTeamRadio(sessionKey);
    }

    public JsonNode getPosition(String sessionKey) {
        return openF1Service.getPosition(sessionKey);
    }

    public JsonNode getIntervals(String sessionKey) {
        return openF1Service.getIntervals(sessionKey);
    }

    public JsonNode getLaps(String sessionKey) {
        return openF1Service.getLaps(sessionKey);
    }

    public JsonNode getOvertakes(String sessionKey) {
        return openF1Service.getOvertakes(sessionKey);
    }

    public JsonNode getStartingGrid(String sessionKey) {
        return openF1Service.getStartingGrid(sessionKey);
    }

    public JsonNode getChampionshipDrivers(String sessionKey) {
        return openF1Service.getChampionshipDrivers(sessionKey);
    }

    public JsonNode getChampionshipTeams(String sessionKey) {
        return openF1Service.getChampionshipTeams(sessionKey);
    }

    public JsonNode getCarData(String sessionKey, Integer driverNumber) {
        return openF1Service.getCarData(sessionKey, driverNumber);
    }

    public JsonNode getLocation(String sessionKey, Integer driverNumber) {
        return openF1Service.getLocation(sessionKey, driverNumber);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @modified 23-05-2026
     * @description Devuelve estado de sesión ligero para todas las páginas live
     * @param requestedSessionKey Clave de sesión opcional
     * @return Estado live
     */
    public JsonNode getLiveStatus(String requestedSessionKey) {
        return cachedBlock("status:" + clean(requestedSessionKey), Duration.ofSeconds(2), () -> {
            LiveSessionResolver.LiveSessionResolution resolution = liveSessionResolver.resolve(requestedSessionKey);
            ObjectNode response = baseResponse(resolution, "status");
            if (resolution.sessionKey().isBlank()) {
                objectChild(response, "messages").put("summary", "Esperando datos de la sesión.");
            } else if (!resolution.usefulData()) {
                objectChild(response, "messages").put("summary", "Sesión resuelta, esperando datos útiles de OpenF1.");
            }
            return response;
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @modified 23-05-2026
     * @description Devuelve solo datos necesarios para el mapa y telemetría breve
     * @param requestedSessionKey Clave de sesión opcional
     * @return Bloque de mapa live
     */
    public JsonNode getLiveMap(String requestedSessionKey) {
        return cachedBlock("map:" + clean(requestedSessionKey), Duration.ofSeconds(1), () -> {
            LiveSessionResolver.LiveSessionResolution resolution = liveSessionResolver.resolve(requestedSessionKey);
            ObjectNode response = baseResponse(resolution, "map");
            String sessionKey = resolution.sessionKey();
            if (sessionKey.isBlank()) {
                emptyFields(response, "drivers", "leaderboard", "position", "intervals", "laps", "stints",
                        "carDataLatest", "locationLatest", "locationTrace");
                objectChild(response, "messages").put("locationLatest", "Esperando telemetría del mapa.");
                return response;
            }

            ArrayNode drivers = mergeStream(sessionKey, "drivers", openF1Service.getDrivers(sessionKey),
                    "driver_number");
            ArrayNode position = latestByDriver(mergeStream(sessionKey, "position",
                    openF1Service.getPosition(sessionKey), "driver_number"));
            ArrayNode intervals = latestByDriver(mergeStream(sessionKey, "intervals",
                    openF1Service.getIntervals(sessionKey), "driver_number"));
            ArrayNode lapsRaw = mergeStream(sessionKey, "laps", openF1Service.getLaps(sessionKey),
                    "driver_number", "lap_number");
            ArrayNode laps = latestCompletedLapByDriver(lapsRaw);
            ArrayNode stints = latestStintByDriver(mergeStream(sessionKey, "stints",
                    openF1Service.getStints(sessionKey), "driver_number", "stint_number", "lap_start"));
            ArrayNode carDataRaw = mergeStream(sessionKey, "carData", openF1Service.getCarData(sessionKey, null, 150),
                    "driver_number", "date");
            ArrayNode locationRaw = mergeStream(sessionKey, "location",
                    openF1Service.getLocation(sessionKey, null, 180), "driver_number", "date");
            ArrayNode carDataLatest = filterFreshByDate(latestByDriver(carDataRaw), 150);
            ArrayNode locationLatest = filterFreshByDate(latestByDriver(locationRaw), 150);
            ArrayNode leaderboard = buildLeaderboard(drivers, objectMapper.createArrayNode(), position, intervals,
                    lapsRaw, laps, stints, carDataLatest);

            response.set("drivers", drivers);
            response.set("leaderboard", leaderboard);
            response.set("position", position);
            response.set("intervals", intervals);
            response.set("laps", laps);
            response.set("stints", stints);
            response.set("carDataLatest", carDataLatest);
            response.set("locationLatest", locationLatest);
            response.set("locationTrace", sampleLocationTrace(locationRaw, 1100));
            putBlockMessages(response);
            return response;
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 23-05-2026
     * @modified 26-05-2026
     * @description Devuelve tabla de tiempos con resultado final o posiciones live
     * @param requestedSessionKey Clave de sesión opcional
     * @return Bloque de timing live
     */
    public JsonNode getLiveTiming(String requestedSessionKey) {
        return cachedBlock("timing:" + clean(requestedSessionKey), Duration.ofSeconds(4), () -> {
            LiveSessionResolver.LiveSessionResolution resolution = liveSessionResolver.resolve(requestedSessionKey);
            ObjectNode response = baseResponse(resolution, "timing");
            String sessionKey = resolution.sessionKey();
            if (sessionKey.isBlank()) {
                emptyFields(response, "drivers", "leaderboard", "sessionResult", "position", "intervals", "laps",
                        "stints");
                objectChild(response, "messages").put("leaderboard", "Esperando clasificación o posiciones confirmadas.");
                return response;
            }

            ArrayNode drivers = mergeStream(sessionKey, "drivers", openF1Service.getDrivers(sessionKey),
                    "driver_number");
            ArrayNode sessionResult = mergeStream(sessionKey, "sessionResult",
                    openF1Service.getSessionResults(sessionKey), "driver_number");
            ArrayNode rankingResult = "Sesión finalizada".equals(resolution.status())
                    ? sessionResult
                    : objectMapper.createArrayNode();
            ArrayNode position = latestByDriver(mergeStream(sessionKey, "position",
                    openF1Service.getPosition(sessionKey), "driver_number"));
            ArrayNode intervals = latestByDriver(mergeStream(sessionKey, "intervals",
                    openF1Service.getIntervals(sessionKey), "driver_number"));
            ArrayNode lapsRaw = mergeStream(sessionKey, "laps", openF1Service.getLaps(sessionKey),
                    "driver_number", "lap_number");
            ArrayNode laps = latestCompletedLapByDriver(lapsRaw);
            ArrayNode stints = latestStintByDriver(mergeStream(sessionKey, "stints",
                    openF1Service.getStints(sessionKey), "driver_number", "stint_number", "lap_start"));

            response.set("drivers", drivers);
            response.set("sessionResult", sessionResult);
            response.set("position", position);
            response.set("intervals", intervals);
            response.set("laps", laps);
            response.set("stints", stints);
            response.set("leaderboard", buildLeaderboard(drivers, rankingResult, position, intervals, lapsRaw, laps,
                    stints, objectMapper.createArrayNode()));
            putBlockMessages(response);
            return response;
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @modified 23-05-2026
     * @description Devuelve actividad de carrera, clima, radio, boxes y adelantamientos
     * @param requestedSessionKey Clave de sesión opcional
     * @return Bloque de carrera live
     */
    public JsonNode getLiveRace(String requestedSessionKey) {
        return cachedBlock("race:" + clean(requestedSessionKey), Duration.ofSeconds(12), () -> {
            LiveSessionResolver.LiveSessionResolution resolution = liveSessionResolver.resolve(requestedSessionKey);
            ObjectNode response = baseResponse(resolution, "race");
            String sessionKey = resolution.sessionKey();
            if (sessionKey.isBlank()) {
                emptyFields(response, "drivers", "raceControl", "weather", "pits", "teamRadio", "overtakes");
                objectChild(response, "messages").put("raceControl", "Esperando datos de dirección de carrera.");
                return response;
            }

            ArrayNode drivers = mergeStream(sessionKey, "drivers", openF1Service.getDrivers(sessionKey),
                    "driver_number");
            ArrayNode lapsRaw = mergeStream(sessionKey, "laps", openF1Service.getLaps(sessionKey),
                    "driver_number", "lap_number");
            ArrayNode overtakes = recentItems(mergeStream(sessionKey, "overtakes",
                    openF1Service.getOvertakes(sessionKey), "date", "overtaking_driver_number",
                    "overtaken_driver_number"), 120);

            response.set("drivers", drivers);
            response.set("raceControl", recentItems(mergeStream(sessionKey, "raceControl",
                    openF1Service.getRaceControl(sessionKey), "date", "message"), 120));
            response.set("weather", recentItems(mergeStream(sessionKey, "weather",
                    openF1Service.getWeather(sessionKey), "date"), 12));
            response.set("pits", recentItems(mergeStream(sessionKey, "pits",
                    openF1Service.getPitStops(sessionKey), "driver_number", "lap_number", "date"), 120));
            response.set("pitStops", response.get("pits"));
            response.set("teamRadio", recentItems(mergeStream(sessionKey, "teamRadio",
                    openF1Service.getTeamRadio(sessionKey), "date", "driver_number"), 120));
            response.set("overtakes", enrichOvertakes(overtakes, lapsRaw));
            putBlockMessages(response);
            return response;
        });
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.2.0
     * @created 21-04-2026
     * @modified 23-05-2026
     * @description Construye una respuesta agregada de compatibilidad. Las nuevas
     *              páginas usan bloques específicos para no saturar OpenF1
     * @param requestedSessionKey Clave de sesión opcional
     * @return Snapshot live agrupado
     */
    public JsonNode getLiveOverview(String requestedSessionKey) {
        ObjectNode response = objectMapper.createObjectNode();
        response.setAll((ObjectNode) getLiveStatus(requestedSessionKey));
        merge(response, (ObjectNode) getLiveMap(requestedSessionKey));
        merge(response, (ObjectNode) getLiveTiming(requestedSessionKey));
        merge(response, (ObjectNode) getLiveRace(requestedSessionKey));
        response.put("block", "overview");
        return response;
    }

    private JsonNode cachedBlock(String cacheKey, Duration ttl, Supplier<ObjectNode> supplier) {
        BlockCacheEntry cached = blockCache.get(cacheKey);
        if (cached != null && cached.isFresh() && !liveDataStreamState.hasChangedSince(cached.fetchedAt())) {
            ObjectNode copy = cached.copy();
            copy.put("fromCache", true);
            return copy;
        }

        ObjectNode fresh = supplier.get();
        fresh.put("fromCache", false);
        if (hasPayload(fresh) || cached == null) {
            blockCache.put(cacheKey, new BlockCacheEntry(fresh.deepCopy(), Instant.now(), ttl));
            return fresh;
        }

        ObjectNode fallback = cached.copy();
        fallback.put("fromCache", true);
        fallback.put("fromLastValid", true);
        objectChild(fallback, "messages").put("summary",
                "OpenF1 ha devuelto vacío temporalmente. Se mantienen los últimos datos válidos.");
        objectChild(fallback, "stale").put(fallback.path("block").asText("block"), true);
        return fallback;
    }

    private ObjectNode baseResponse(LiveSessionResolver.LiveSessionResolution resolution, String block) {
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode session = enrichSession(resolution.session());
        response.put("generatedAt", Instant.now().toString());
        response.put("block", block);
        response.put("fromCache", false);
        response.put("fromLastValid", false);
        response.put("status", resolution.status());
        response.put("sessionKey", resolution.sessionKey());
        response.put("hasUsefulData", resolution.usefulData());
        response.set("session", session);
        ObjectNode metric = sessionMetric(resolution, session);
        if (!metric.isEmpty()) {
            response.set("sessionMetric", metric);
        }
        response.putObject("stale");
        response.putObject("messages");
        return response;
    }

    private ObjectNode enrichSession(ObjectNode session) {
        ObjectNode result = session == null ? objectMapper.createObjectNode() : session.deepCopy();
        if (result.isEmpty()) {
            return result;
        }
        int meetingKey = result.path("meeting_key").asInt(-1);
        if (meetingKey <= 0) {
            return result;
        }
        ObjectNode meeting = firstObject(openF1Service.getMeeting(meetingKey));
        if (!meeting.isEmpty()) {
            copyIfMissing(result, meeting, "meeting_official_name");
            copyIfMissing(result, meeting, "meeting_name");
            copyIfMissing(result, meeting, "country_name");
            copyIfMissing(result, meeting, "location");
            copyIfMissing(result, meeting, "gmt_offset");
        }
        JsonNode calendarMeeting = f1ScheduleService.getMeetingByKey(meetingKey);
        copyIfMissing(result, calendarMeeting, "total_laps");
        copyIfMissing(result, calendarMeeting, "race_distance");
        copyIfMissing(result, calendarMeeting, "circuit_lap_record");
        return result;
    }

    private ObjectNode sessionMetric(LiveSessionResolver.LiveSessionResolution resolution, ObjectNode session) {
        ObjectNode metric = objectMapper.createObjectNode();
        if (resolution.sessionKey().isBlank() || session == null || session.isEmpty()) {
            return metric;
        }
        String sessionName = firstText(session, "session_name").toLowerCase();
        if (sessionName.contains("race") || sessionName.equals("sprint")) {
            ArrayNode laps = mergeStream(resolution.sessionKey(), "laps",
                    openF1Service.getLaps(resolution.sessionKey()), "driver_number", "lap_number");
            int currentLap = maxLap(laps);
            int totalLaps = firstPositiveInt(session, "total_laps", "laps", "scheduled_laps", "race_laps");
            metric.put("label", "Vueltas");
            metric.put("value", currentLap > 0
                    ? currentLap + (totalLaps > 0 ? "/" + totalLaps : "")
                    : (totalLaps > 0 ? DASH + "/" + totalLaps : DASH));
            return metric;
        }
        if (sessionName.contains("qualifying") || sessionName.contains("shootout")) {
            metric.put("label", "Clasificación");
            metric.put("value", qualificationPhase(openF1Service.getRaceControl(resolution.sessionKey()), session)
                    + remainingSuffix(session));
            return metric;
        }
        if (sessionName.contains("practice")) {
            metric.put("label", "Tiempo restante");
            metric.put("value", remainingTime(session));
        }
        return metric;
    }

    private void merge(ObjectNode target, ObjectNode source) {
        source.fields().forEachRemaining(entry -> {
            if (!List.of("block", "fromCache", "fromLastValid").contains(entry.getKey())) {
                target.set(entry.getKey(), entry.getValue());
            }
        });
    }

    private boolean hasPayload(ObjectNode node) {
        if ("status".equals(node.path("block").asText())) {
            return true;
        }
        List<String> metaFields = List.of("generatedAt", "block", "fromCache", "fromLastValid", "status",
                "sessionKey", "hasUsefulData", "session", "sessionMetric", "stale", "messages");
        var fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (metaFields.contains(entry.getKey())) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (value.isArray() && !value.isEmpty()) {
                return true;
            }
            if (value.isObject() && !value.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void putBlockMessages(ObjectNode response) {
        ObjectNode messages = objectChild(response, "messages");
        putEmptyMessage(messages, response, "leaderboard", "Esperando clasificación o posiciones confirmadas.");
        putEmptyMessage(messages, response, "weather",
                "El clima aparecerá cuando OpenF1 publique mediciones para esta sesión.");
        putEmptyMessage(messages, response, "raceControl",
                "Dirección de carrera no ha publicado mensajes para esta sesión.");
        putEmptyMessage(messages, response, "pits", "Las paradas en boxes solo suelen aparecer en carrera o sprint.");
        putEmptyMessage(messages, response, "teamRadio", "Team radio no está disponible en todos los eventos.");
        putEmptyMessage(messages, response, "overtakes",
                "Endpoint no disponible para esta sesión si no es carrera o sprint.");
        putEmptyMessage(messages, response, "locationLatest", "Esperando telemetría del mapa.");
        putEmptyMessage(messages, response, "carDataLatest", "Esperando telemetría del coche.");
    }

    private void putEmptyMessage(ObjectNode messages, ObjectNode response, String field, String message) {
        JsonNode node = response.get(field);
        if (node == null || (node.isArray() && node.isEmpty())) {
            messages.put(field, message);
        }
    }

    private void emptyFields(ObjectNode response, String... fields) {
        for (String field : fields) {
            response.set(field, objectMapper.createArrayNode());
        }
    }

    private ArrayNode buildLeaderboard(
            ArrayNode drivers,
            ArrayNode sessionResult,
            ArrayNode position,
            ArrayNode intervals,
            ArrayNode lapsRaw,
            ArrayNode laps,
            ArrayNode stints,
            ArrayNode carDataLatest) {
        Map<Integer, JsonNode> driversByNumber = byDriver(drivers);
        Map<Integer, JsonNode> resultByNumber = byDriver(sessionResult);
        Map<Integer, JsonNode> positionByNumber = byDriver(position);
        Map<Integer, JsonNode> intervalsByNumber = byDriver(intervals);
        Map<Integer, JsonNode> latestLapByNumber = byDriver(laps);
        Map<Integer, JsonNode> latestSectorLapByNumber = latestSectorLapByDriver(lapsRaw);
        Map<Integer, JsonNode> bestLapByNumber = bestLapByDriver(lapsRaw);
        Map<Integer, JsonNode> stintsByNumber = byDriver(stints);
        Map<Integer, JsonNode> carByNumber = byDriver(carDataLatest);
        SectorStats sectorStats = new SectorStats(lapsRaw);
        ArrayNode source = !sessionResult.isEmpty() ? sessionResult : (!drivers.isEmpty() ? drivers : position);
        ArrayNode rows = objectMapper.createArrayNode();

        source.forEach(item -> {
            int driverNumber = item.path("driver_number").asInt(-1);
            if (driverNumber <= 0) {
                return;
            }
            JsonNode driver = driversByNumber.getOrDefault(driverNumber, item);
            JsonNode result = resultByNumber.getOrDefault(driverNumber, item);
            JsonNode positionRow = positionByNumber.getOrDefault(driverNumber, item);
            JsonNode interval = intervalsByNumber.getOrDefault(driverNumber, objectMapper.createObjectNode());
            JsonNode latestLap = latestLapByNumber.getOrDefault(driverNumber, objectMapper.createObjectNode());
            JsonNode latestSectorLap = latestSectorLapByNumber.getOrDefault(driverNumber, latestLap);
            JsonNode bestLap = bestLapByNumber.getOrDefault(driverNumber, objectMapper.createObjectNode());
            JsonNode stint = stintsByNumber.getOrDefault(driverNumber, objectMapper.createObjectNode());
            JsonNode car = carByNumber.getOrDefault(driverNumber, objectMapper.createObjectNode());
            ObjectNode row = objectMapper.createObjectNode();
            row.put("position", text(firstText(result, "position", "classified_position"),
                    firstText(positionRow, "position")));
            row.put("driverNumber", driverNumber);
            row.put("driver", driverName(driver, driverNumber));
            row.put("team", text(firstText(driver, "team_name"), firstText(result, "team_name")));
            row.put("teamColour", normalizeColour(firstText(driver, "team_colour", "team_color")));
            row.put("headshotUrl", firstText(driver, "headshot_url"));
            row.put("gap", text(firstText(interval, "gap_to_leader"), firstText(result, "gap_to_leader")));
            row.put("interval", text(firstText(interval, "interval"), firstText(result, "interval")));
            row.put("bestLap", text(firstText(bestLap, "lap_duration")));
            row.put("lastLap", text(firstText(latestLap, "lap_duration")));
            row.put("currentLap", currentLap(latestLap, car));
            row.put("tyre", text(firstText(stint, "compound")));
            row.put("tyreLaps", tyreLaps(latestLap, stint));
            putSector(row, "s1", latestSectorLap, "duration_sector_1", sectorStats);
            putSector(row, "s2", latestSectorLap, "duration_sector_2", sectorStats);
            putSector(row, "s3", latestSectorLap, "duration_sector_3", sectorStats);
            row.put("throttle", text(firstText(car, "throttle")));
            row.put("brake", text(firstText(car, "brake")));
            row.put("lastUpdated", text(firstText(car, "date"), firstText(positionRow, "date"),
                    firstText(latestLap, "date")));
            rows.add(row);
        });

        ArrayNode sorted = objectMapper.createArrayNode();
        List<JsonNode> rowList = new ArrayList<>();
        rows.forEach(rowList::add);
        rowList.sort(Comparator.comparingInt(item -> safeInt(item.path("position").asText(), 999)));
        rowList.forEach(sorted::add);
        return sorted;
    }

    private void putSector(ObjectNode row, String prefix, JsonNode latestLap, String field, SectorStats stats) {
        String value = text(firstText(latestLap, field));
        row.put(prefix, value);
        row.put(prefix + "Status", stats.status(latestLap.path("driver_number").asInt(-1), field, value));
    }

    private String tyreLaps(JsonNode latestLap, JsonNode stint) {
        int currentLap = latestLap.path("lap_number").asInt(-1);
        int startLap = stint.path("lap_start").asInt(-1);
        if (currentLap > 0 && startLap > 0) {
            return String.valueOf(Math.max(1, currentLap - startLap + 1));
        }
        return text(firstText(stint, "tyre_age_at_start"));
    }

    private Map<Integer, JsonNode> latestSectorLapByDriver(ArrayNode laps) {
        Map<Integer, JsonNode> result = new LinkedHashMap<>();
        laps.forEach(item -> {
            int driverNumber = item.path("driver_number").asInt(-1);
            if (driverNumber <= 0 || !hasAnySector(item)) {
                return;
            }
            JsonNode current = result.get(driverNumber);
            if (current == null || item.path("lap_number").asInt(-1) > current.path("lap_number").asInt(-1)
                    || (item.path("lap_number").asInt(-1) == current.path("lap_number").asInt(-1)
                            && safeInstant(item.path("date_start").asText(""))
                                    .isAfter(safeInstant(current.path("date_start").asText(""))))) {
                result.put(driverNumber, item);
            }
        });
        return result;
    }

    private boolean hasAnySector(JsonNode lap) {
        return hasTiming(lap, "duration_sector_1")
                || hasTiming(lap, "duration_sector_2")
                || hasTiming(lap, "duration_sector_3");
    }

    private boolean hasTiming(JsonNode node, String field) {
        double value = number(node == null ? "" : node.path(field).asText(""));
        return value != Double.MAX_VALUE && value > 0;
    }

    private Map<Integer, JsonNode> byDriver(ArrayNode source) {
        Map<Integer, JsonNode> result = new LinkedHashMap<>();
        source.forEach(item -> {
            int driverNumber = item.path("driver_number").asInt(-1);
            if (driverNumber > 0) {
                result.put(driverNumber, item);
            }
        });
        return result;
    }

    private Map<Integer, JsonNode> bestLapByDriver(ArrayNode laps) {
        Map<Integer, JsonNode> result = new LinkedHashMap<>();
        laps.forEach(item -> {
            int driverNumber = item.path("driver_number").asInt(-1);
            double duration = number(item.path("lap_duration").asText(""));
            if (driverNumber <= 0 || duration == Double.MAX_VALUE || duration <= 0) {
                return;
            }
            JsonNode current = result.get(driverNumber);
            if (current == null || duration < number(current.path("lap_duration").asText(""))) {
                result.put(driverNumber, item);
            }
        });
        return result;
    }

    private ArrayNode latestCompletedLapByDriver(ArrayNode laps) {
        Map<Integer, JsonNode> result = new LinkedHashMap<>();
        laps.forEach(item -> {
            int driverNumber = item.path("driver_number").asInt(-1);
            double duration = number(item.path("lap_duration").asText(""));
            if (driverNumber <= 0 || duration == Double.MAX_VALUE || duration <= 0) {
                return;
            }
            JsonNode current = result.get(driverNumber);
            if (current == null || item.path("lap_number").asInt(-1) > current.path("lap_number").asInt(-1)) {
                result.put(driverNumber, item);
            }
        });
        ArrayNode rows = objectMapper.createArrayNode();
        result.values().forEach(item -> rows.add(item.deepCopy()));
        return rows;
    }

    private String currentLap(JsonNode latestCompletedLap, JsonNode car) {
        int completedLap = latestCompletedLap.path("lap_number").asInt(-1);
        if (completedLap <= 0) {
            return DASH;
        }
        Instant telemetryDate = safeInstant(car.path("date").asText(""));
        boolean telemetryFresh = !Instant.EPOCH.equals(telemetryDate)
                && telemetryDate.isAfter(Instant.now().minusSeconds(150));
        return String.valueOf(telemetryFresh ? completedLap + 1 : completedLap);
    }

    private ArrayNode sampleLocationTrace(ArrayNode source, int limit) {
        ArrayNode result = objectMapper.createArrayNode();
        if (source.isEmpty()) {
            return result;
        }
        int step = Math.max(1, source.size() / Math.max(1, limit));
        for (int i = 0; i < source.size(); i += step) {
            result.add(source.get(i).deepCopy());
        }
        return result;
    }

    private ArrayNode filterFreshByDate(ArrayNode source, long maxAgeSeconds) {
        ArrayNode result = objectMapper.createArrayNode();
        Instant threshold = Instant.now().minusSeconds(maxAgeSeconds);
        source.forEach(item -> {
            Instant date = safeInstant(item.path("date").asText(""));
            if (!Instant.EPOCH.equals(date) && date.isAfter(threshold)) {
                result.add(item.deepCopy());
            }
        });
        return result;
    }

    private ArrayNode enrichOvertakes(ArrayNode overtakes, ArrayNode laps) {
        ArrayNode result = objectMapper.createArrayNode();
        overtakes.forEach(item -> {
            ObjectNode row = item.deepCopy();
            if (!row.hasNonNull("lap_number")) {
                row.put("lapNumber", lapNumberForDate(
                        row.path("overtaking_driver_number").asInt(-1),
                        row.path("date").asText(""),
                        laps));
            }
            result.add(row);
        });
        return result;
    }

    private String lapNumberForDate(int driverNumber, String date, ArrayNode laps) {
        Instant instant = safeInstant(date);
        if (driverNumber <= 0 || Instant.EPOCH.equals(instant)) {
            return DASH;
        }
        int lapNumber = -1;
        for (JsonNode lap : laps) {
            if (lap.path("driver_number").asInt(-1) != driverNumber) {
                continue;
            }
            Instant lapStart = safeInstant(lap.path("date_start").asText(""));
            if (!Instant.EPOCH.equals(lapStart) && !lapStart.isAfter(instant)) {
                lapNumber = Math.max(lapNumber, lap.path("lap_number").asInt(-1));
            }
        }
        return lapNumber > 0 ? String.valueOf(lapNumber) : DASH;
    }

    private ArrayNode mergeStream(String sessionKey, String field, JsonNode restItems, String... keyFields) {
        Map<String, JsonNode> rows = new LinkedHashMap<>();
        array(restItems).forEach(item -> rows.put(rowKey(item, keyFields), item.deepCopy()));
        liveDataStreamState.getItems(sessionKey, field)
                .forEach(item -> rows.put(rowKey(item, keyFields), item.deepCopy()));
        ArrayNode result = objectMapper.createArrayNode();
        rows.values().forEach(result::add);
        return result;
    }

    private String rowKey(JsonNode item, String... keyFields) {
        String key = firstText(item, "_key", "_id");
        if (!key.isBlank()) {
            return key;
        }
        List<String> parts = new ArrayList<>();
        for (String field : keyFields) {
            String value = firstText(item, field);
            if (!value.isBlank()) {
                parts.add(value);
            }
        }
        if (!parts.isEmpty()) {
            return String.join(":", parts);
        }
        return firstText(item, "date", "date_start") + ":" + item.hashCode();
    }

    private ArrayNode latestStintByDriver(JsonNode items) {
        Map<Integer, JsonNode> rows = new LinkedHashMap<>();
        if (items != null && items.isArray()) {
            items.forEach(item -> {
                int driverNumber = item.path("driver_number").asInt(-1);
                if (driverNumber <= 0) {
                    return;
                }
                JsonNode current = rows.get(driverNumber);
                if (current == null || stintSortValue(item) >= stintSortValue(current)) {
                    rows.put(driverNumber, item);
                }
            });
        }
        ArrayNode result = objectMapper.createArrayNode();
        rows.values().forEach(item -> result.add(item.deepCopy()));
        return result;
    }

    private int stintSortValue(JsonNode item) {
        int stint = item.path("stint_number").asInt(-1);
        int lapStart = item.path("lap_start").asInt(-1);
        return Math.max(stint, 0) * 1000 + Math.max(lapStart, 0);
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
        rows.values().forEach(item -> result.add(item.deepCopy()));
        return result;
    }

    private ArrayNode recentItems(JsonNode items, int limit) {
        List<JsonNode> rows = new ArrayList<>();
        array(items).forEach(item -> rows.add(item.deepCopy()));
        rows.sort((left, right) -> safeInstant(firstText(right, "date", "date_start", "created_at"))
                .compareTo(safeInstant(firstText(left, "date", "date_start", "created_at"))));
        ArrayNode result = objectMapper.createArrayNode();
        rows.stream().limit(Math.max(0, limit)).forEach(result::add);
        return result;
    }

    private ArrayNode array(JsonNode node) {
        ArrayNode result = objectMapper.createArrayNode();
        if (node != null && node.isArray()) {
            node.forEach(item -> result.add(item.deepCopy()));
        }
        return result;
    }

    private ObjectNode firstObject(JsonNode node) {
        if (node != null && node.isArray() && !node.isEmpty() && node.get(0).isObject()) {
            return (ObjectNode) node.get(0).deepCopy();
        }
        return objectMapper.createObjectNode();
    }

    private void copyIfMissing(ObjectNode target, JsonNode source, String field) {
        if (target.hasNonNull(field) || source == null || !source.hasNonNull(field)) {
            return;
        }
        target.set(field, source.get(field).deepCopy());
    }

    private int maxLap(ArrayNode laps) {
        int max = -1;
        for (JsonNode lap : laps) {
            max = Math.max(max, lap.path("lap_number").asInt(-1));
        }
        return max;
    }

    private int firstPositiveInt(JsonNode node, String... fields) {
        if (node == null) {
            return -1;
        }
        for (String field : fields) {
            int value = node.path(field).asInt(-1);
            if (value > 0) {
                return value;
            }
        }
        return -1;
    }

    private String qualificationPhase(JsonNode raceControl, ObjectNode session) {
        String fallback = firstText(session, "session_name").toLowerCase().contains("sprint") ? "SQ" : "Q";
        ArrayNode messages = recentItems(raceControl, 30);
        for (JsonNode item : messages) {
            String text = (item.path("message").asText("") + " "
                    + item.path("category").asText("") + " "
                    + item.path("flag").asText("")).toUpperCase();
            if (text.contains("SQ3")) {
                return "SQ3";
            }
            if (text.contains("SQ2")) {
                return "SQ2";
            }
            if (text.contains("SQ1")) {
                return "SQ1";
            }
            if (text.contains("Q3")) {
                return "Q3";
            }
            if (text.contains("Q2")) {
                return "Q2";
            }
            if (text.contains("Q1")) {
                return "Q1";
            }
        }
        return fallback;
    }

    private String remainingSuffix(ObjectNode session) {
        String remaining = remainingTime(session);
        return DASH.equals(remaining) ? "" : " · " + remaining;
    }

    private String remainingTime(ObjectNode session) {
        Instant end = safeInstant(firstText(session, "date_end"));
        if (Instant.EPOCH.equals(end)) {
            return DASH;
        }
        long seconds = Duration.between(Instant.now(), end).toSeconds();
        if (seconds <= 0) {
            return "En directo";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long rest = seconds % 60;
        if (hours > 0) {
            return "%dh %02dm".formatted(hours, minutes);
        }
        return "%02dm %02ds".formatted(minutes, rest);
    }

    private String driverName(JsonNode driver, int number) {
        String fullName = firstText(driver, "full_name");
        if (!fullName.isBlank()) {
            return fullName;
        }
        String acronym = firstText(driver, "name_acronym");
        return acronym.isBlank() ? "#" + number : acronym;
    }

    private String normalizeColour(String value) {
        String colour = value.replace("#", "").trim();
        return colour.matches("[0-9A-Fa-f]{6}") ? "#" + colour : "#e5e7eb";
    }

    private String text(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return DASH;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return "";
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText("");
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private int safeInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9-]", ""));
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private double number(String value) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException ex) {
            return Double.MAX_VALUE;
        }
    }

    private Instant safeInstant(String value) {
        try {
            return clean(value).isBlank() ? Instant.EPOCH : Instant.parse(value);
        } catch (RuntimeException ex) {
            return Instant.EPOCH;
        }
    }

    private ObjectNode objectChild(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node != null && node.isObject()) {
            return (ObjectNode) node;
        }
        ObjectNode child = objectMapper.createObjectNode();
        parent.set(field, child);
        return child;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private class SectorStats {
        private final Map<String, Double> global = new LinkedHashMap<>();
        private final Map<String, Map<Integer, Double>> personal = new LinkedHashMap<>();

        SectorStats(ArrayNode laps) {
            for (String field : new String[] { "duration_sector_1", "duration_sector_2", "duration_sector_3" }) {
                global.put(field, Double.MAX_VALUE);
                personal.put(field, new LinkedHashMap<>());
            }
            laps.forEach(lap -> {
                int driverNumber = lap.path("driver_number").asInt(-1);
                personal.keySet().forEach(field -> {
                    double value = number(lap.path(field).asText(""));
                    if (value == Double.MAX_VALUE) {
                        return;
                    }
                    global.put(field, Math.min(global.get(field), value));
                    personal.get(field).merge(driverNumber, value, Math::min);
                });
            });
        }

        String status(int driverNumber, String field, String value) {
            double sector = number(value);
            if (sector == Double.MAX_VALUE) {
                return "neutral";
            }
            if (Math.abs(sector - global.getOrDefault(field, Double.MAX_VALUE)) < 0.0001) {
                return "purple";
            }
            if (Math.abs(sector - personal.getOrDefault(field, Map.of())
                    .getOrDefault(driverNumber, Double.MAX_VALUE)) < 0.0001) {
                return "green";
            }
            return "yellow";
        }
    }

    private record BlockCacheEntry(ObjectNode data, Instant fetchedAt, Duration ttl) {
        private boolean isFresh() {
            return Instant.now().isBefore(fetchedAt.plus(ttl));
        }

        private ObjectNode copy() {
            return data.deepCopy();
        }
    }
}

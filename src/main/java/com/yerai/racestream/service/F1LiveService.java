/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.3.0
 * @created 21-04-2026
 * @modified 23-05-2026
 * @description Servicio del Live Center que construye bloques ligeros,
 *              cacheados y tolerantes a sesiones retrasadas de OpenF1
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
    private final ObjectMapper objectMapper;
    private final Map<String, BlockCacheEntry> blockCache = new ConcurrentHashMap<>();

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.2.0
     * @created 21-04-2026
     * @modified 23-05-2026
     * @description Constructor con dependencias necesarias para consultar OpenF1,
     *              resolver la sesión live y construir respuestas JSON
     * @param openF1Service Servicio base de OpenF1
     * @param liveSessionResolver Resolvedor central de sesiones live
     * @param objectMapper Mapper para construir respuestas agregadas
     */
    public F1LiveService(OpenF1Service openF1Service, LiveSessionResolver liveSessionResolver, ObjectMapper objectMapper) {
        this.openF1Service = openF1Service;
        this.liveSessionResolver = liveSessionResolver;
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

            ArrayNode drivers = array(openF1Service.getDrivers(sessionKey));
            ArrayNode position = latestByDriver(openF1Service.getPosition(sessionKey));
            ArrayNode intervals = latestByDriver(openF1Service.getIntervals(sessionKey));
            ArrayNode lapsRaw = array(openF1Service.getLaps(sessionKey));
            ArrayNode laps = latestCompletedLapByDriver(lapsRaw);
            ArrayNode stints = latestByDriver(openF1Service.getStints(sessionKey));
            ArrayNode carDataRaw = array(openF1Service.getCarData(sessionKey, null, 90));
            ArrayNode locationRaw = array(openF1Service.getLocation(sessionKey, null, 240));
            ArrayNode carDataLatest = latestByDriver(carDataRaw);
            ArrayNode locationLatest = latestByDriver(locationRaw);
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
     * @version 1.0.0
     * @created 23-05-2026
     * @modified 23-05-2026
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

            ArrayNode drivers = array(openF1Service.getDrivers(sessionKey));
            ArrayNode sessionResult = array(openF1Service.getSessionResults(sessionKey));
            ArrayNode position = latestByDriver(openF1Service.getPosition(sessionKey));
            ArrayNode intervals = latestByDriver(openF1Service.getIntervals(sessionKey));
            ArrayNode lapsRaw = array(openF1Service.getLaps(sessionKey));
            ArrayNode laps = latestCompletedLapByDriver(lapsRaw);
            ArrayNode stints = latestByDriver(openF1Service.getStints(sessionKey));

            response.set("drivers", drivers);
            response.set("sessionResult", sessionResult);
            response.set("position", position);
            response.set("intervals", intervals);
            response.set("laps", laps);
            response.set("stints", stints);
            response.set("leaderboard", buildLeaderboard(drivers, sessionResult, position, intervals, lapsRaw, laps,
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

            ArrayNode drivers = array(openF1Service.getDrivers(sessionKey));
            ArrayNode lapsRaw = array(openF1Service.getLaps(sessionKey));
            ArrayNode overtakes = lastItems(openF1Service.getOvertakes(sessionKey), 18);

            response.set("drivers", drivers);
            response.set("raceControl", lastItems(openF1Service.getRaceControl(sessionKey), 18));
            response.set("weather", lastItems(openF1Service.getWeather(sessionKey), 4));
            response.set("pits", lastItems(openF1Service.getPitStops(sessionKey), 18));
            response.set("pitStops", response.get("pits"));
            response.set("teamRadio", lastItems(openF1Service.getTeamRadio(sessionKey), 12));
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
        if (cached != null && cached.isFresh()) {
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
        response.put("generatedAt", Instant.now().toString());
        response.put("block", block);
        response.put("fromCache", false);
        response.put("fromLastValid", false);
        response.put("status", resolution.status());
        response.put("sessionKey", resolution.sessionKey());
        response.put("hasUsefulData", resolution.usefulData());
        response.set("session", resolution.session());
        response.putObject("stale");
        response.putObject("messages");
        return response;
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
                "sessionKey", "hasUsefulData", "session", "stale", "messages");
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
            putSector(row, "s1", latestLap, "duration_sector_1", sectorStats);
            putSector(row, "s2", latestLap, "duration_sector_2", sectorStats);
            putSector(row, "s3", latestLap, "duration_sector_3", sectorStats);
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
        if (currentLap > 0 && startLap > 0 && currentLap >= startLap) {
            return String.valueOf(currentLap - startLap + 1);
        }
        return text(firstText(stint, "tyre_age_at_start"));
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
            if (driverNumber <= 0 || duration <= 0) {
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
            if (driverNumber <= 0 || duration <= 0) {
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

    private ArrayNode lastItems(JsonNode items, int limit) {
        ArrayNode source = array(items);
        ArrayNode result = objectMapper.createArrayNode();
        int start = Math.max(0, source.size() - limit);
        for (int i = start; i < source.size(); i++) {
            result.add(source.get(i).deepCopy());
        }
        return result;
    }

    private ArrayNode array(JsonNode node) {
        ArrayNode result = objectMapper.createArrayNode();
        if (node != null && node.isArray()) {
            node.forEach(item -> result.add(item.deepCopy()));
        }
        return result;
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

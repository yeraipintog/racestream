/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 24-05-2026
 * @modified 24-05-2026
 * @description Estado en memoria para fusionar mensajes MQTT de OpenF1 con los
 *              snapshots REST del Live Center sin exponer tokens al navegador
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LiveDataStreamState {

    private final ObjectMapper objectMapper;
    private final Map<String, SessionLiveState> sessions = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();
    private volatile Instant lastUpdateAt = Instant.EPOCH;

    public LiveDataStreamState(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 24-05-2026
     * @modified 24-05-2026
     * @description Guarda un mensaje recibido por streaming y devuelve el evento
     *              seguro que se puede emitir por SSE
     * @param topic Tema MQTT recibido
     * @param payload JSON recibido desde OpenF1
     * @return Evento normalizado o null si no pertenece a una sesión
     */
    public ObjectNode update(String topic, JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return null;
        }

        String field = fieldForTopic(topic);
        if (field.isBlank()) {
            return null;
        }

        ObjectNode row = ((ObjectNode) payload).deepCopy();
        String sessionKey = row.path("session_key").asText("");
        if (sessionKey.isBlank()) {
            return null;
        }

        Instant now = Instant.now();
        row.put("stream_topic", topic);
        row.put("stream_received_at", now.toString());
        sessions.computeIfAbsent(sessionKey, key -> new SessionLiveState()).put(field, row);
        long currentVersion = version.incrementAndGet();
        lastUpdateAt = now;

        ObjectNode event = objectMapper.createObjectNode();
        event.put("topic", topic);
        event.put("field", field);
        event.put("sessionKey", sessionKey);
        event.put("version", currentVersion);
        event.put("receivedAt", now.toString());
        event.set("data", row.deepCopy());
        return event;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 24-05-2026
     * @modified 24-05-2026
     * @description Devuelve filas de streaming para una sesión y bloque concreto
     * @param sessionKey Clave de sesión OpenF1
     * @param field Campo interno solicitado
     * @return Array seguro con datos recientes
     */
    public ArrayNode getItems(String sessionKey, String field) {
        SessionLiveState state = sessions.get(clean(sessionKey));
        return state == null ? objectMapper.createArrayNode() : state.copy(field, objectMapper);
    }

    public boolean hasChangedSince(Instant instant) {
        return lastUpdateAt.isAfter(instant == null ? Instant.EPOCH : instant);
    }

    public long version() {
        return version.get();
    }

    private String fieldForTopic(String topic) {
        return switch (clean(topic)) {
            case "v1/sessions" -> "sessions";
            case "v1/drivers" -> "drivers";
            case "v1/position" -> "position";
            case "v1/intervals" -> "intervals";
            case "v1/laps" -> "laps";
            case "v1/stints" -> "stints";
            case "v1/pit" -> "pits";
            case "v1/race_control" -> "raceControl";
            case "v1/team_radio" -> "teamRadio";
            case "v1/overtakes" -> "overtakes";
            case "v1/weather" -> "weather";
            case "v1/car_data" -> "carData";
            case "v1/location" -> "location";
            case "v1/session_result" -> "sessionResult";
            default -> "";
        };
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private class SessionLiveState {
        private final Map<String, BoundedRows> rowsByField = new ConcurrentHashMap<>();

        private void put(String field, ObjectNode row) {
            rowsByField.computeIfAbsent(field, key -> new BoundedRows(limitFor(key))).put(stableKey(field, row), row);
        }

        private ArrayNode copy(String field, ObjectMapper mapper) {
            BoundedRows rows = rowsByField.get(field);
            return rows == null ? mapper.createArrayNode() : rows.copy(mapper);
        }

        private int limitFor(String field) {
            return switch (field) {
                case "location" -> 3000;
                case "carData" -> 1200;
                case "laps" -> 2200;
                case "raceControl", "teamRadio", "overtakes", "pits" -> 240;
                case "weather" -> 80;
                default -> 80;
            };
        }

        private String stableKey(String field, JsonNode row) {
            String explicitKey = row.path("_key").asText("");
            if (!explicitKey.isBlank()) {
                return explicitKey;
            }
            String explicitId = row.path("_id").asText("");
            if (!explicitId.isBlank()) {
                return explicitId;
            }
            if ("laps".equals(field)) {
                return row.path("driver_number").asText("") + ":" + row.path("lap_number").asText("");
            }
            if ("drivers".equals(field) || "position".equals(field) || "intervals".equals(field)) {
                return row.path("driver_number").asText("");
            }
            if ("stints".equals(field)) {
                return row.path("driver_number").asText("") + ":" + row.path("stint_number").asText("")
                        + ":" + row.path("lap_start").asText("");
            }
            if ("sessions".equals(field)) {
                return row.path("session_key").asText("");
            }
            return row.path("date").asText(row.path("date_start").asText(""))
                    + ":" + row.path("driver_number").asText("")
                    + ":" + row.path("message").asText("");
        }
    }

    private class BoundedRows {
        private final int limit;
        private final LinkedHashMap<String, ObjectNode> rows = new LinkedHashMap<>();

        private BoundedRows(int limit) {
            this.limit = limit;
        }

        private synchronized void put(String key, ObjectNode row) {
            String safeKey = key == null || key.isBlank()
                    ? String.valueOf(System.nanoTime())
                    : key;
            rows.remove(safeKey);
            rows.put(safeKey, row.deepCopy());
            while (rows.size() > limit) {
                String oldestKey = rows.keySet().iterator().next();
                rows.remove(oldestKey);
            }
        }

        private synchronized ArrayNode copy(ObjectMapper mapper) {
            ArrayNode result = mapper.createArrayNode();
            rows.values().forEach(row -> result.add(row.deepCopy()));
            return result;
        }
    }
}

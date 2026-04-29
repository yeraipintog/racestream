/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 29-04-2026
 * @description Servicio para obtener datos tecnicos de circuitos desde F1DB sin introducir datos manuales
 * @see https://github.com/f1db/f1db
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class F1DbService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<Integer, ArrayNode> circuitsCache = new ConcurrentHashMap<>();
    private F1DbData dataCache;

    @Value("${f1db.splitted-json-url:https://github.com/f1db/f1db/releases/latest/download/f1db-json-splitted.zip}")
    private String f1dbSplittedJsonUrl;

    public F1DbService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Devuelve circuitos enriquecidos por temporada desde F1DB
     * @param year Temporada
     * @return Circuitos normalizados para RaceStream
     */
    public synchronized ArrayNode getCircuits(Integer year) {
        Integer selectedYear = year == null ? 2026 : year;
        ArrayNode cached = circuitsCache.get(selectedYear);
        if (cached != null) {
            return cached.deepCopy();
        }

        F1DbData data = getData();
        ArrayNode circuits = objectMapper.createArrayNode();

        for (JsonNode race : data.races) {
            if (race.path("year").asInt() == selectedYear) {
                circuits.add(buildRaceCircuit(race, data));
            }
        }

        data.circuits.forEach(circuit -> circuits.add(buildBaseCircuit(circuit, data)));
        if (!circuits.isEmpty()) {
            circuitsCache.put(selectedYear, circuits.deepCopy());
        }
        return circuits.deepCopy();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Construye circuito a partir de una carrera F1DB para obtener vueltas y distancia reales
     * @param race Carrera F1DB
     * @param data Datos cacheados
     * @return Circuito normalizado
     */
    private ObjectNode buildRaceCircuit(JsonNode race, F1DbData data) {
        JsonNode circuit = data.circuitsById.getOrDefault(getText(race, "circuitId"), objectMapper.createObjectNode());
        ObjectNode node = buildBaseCircuit(circuit, data);
        node.put("source", "f1db-race");
        putIfPresent(node, "raceId", getText(race, "id"));
        putIfPresent(node, "year", getText(race, "year"));
        putIfPresent(node, "round", getText(race, "round"));
        putIfPresent(node, "grandPrixId", getText(race, "grandPrixId"));
        putIfPresent(node, "circuitLayoutId", getText(race, "circuitLayoutId"));
        putIfPresent(node, "circuitType", getText(race, "circuitType"));
        putIfPresent(node, "circuitLength", kilometersToMeters(race.get("courseLength")));
        putIfPresent(node, "numberOfCorners", getText(race, "turns"));
        putIfPresent(node, "numberOfLaps", getText(race, "laps"));
        putIfPresent(node, "raceDistance", kilometersToMeters(race.get("distance")));
        applyLapRecord(node, race, data);
        return node;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Construye datos base del circuito cuando no hay carrera concreta
     * @param circuit Circuito F1DB
     * @param data Datos cacheados
     * @return Circuito normalizado
     */
    private ObjectNode buildBaseCircuit(JsonNode circuit, F1DbData data) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("source", "f1db");
        putIfPresent(node, "circuitId", getText(circuit, "id"));
        putIfPresent(node, "circuitName", getText(circuit, "fullName"));
        putIfPresent(node, "city", getText(circuit, "placeName"));
        putIfPresent(node, "country", getText(circuit, "countryId"));
        putIfPresent(node, "circuitType", getText(circuit, "type"));
        putIfPresent(node, "circuitLength", kilometersToMeters(circuit.get("length")));
        putIfPresent(node, "numberOfCorners", getText(circuit, "turns"));
        applyLapRecord(node, circuit, data);
        return node;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Aplica la vuelta rapida historica calculada por circuito
     * @param target Circuito destino
     * @param source Nodo con circuitId
     * @param data Datos cacheados
     */
    private void applyLapRecord(ObjectNode target, JsonNode source, F1DbData data) {
        JsonNode record = data.fastestLapByCircuitId.get(getText(source, "circuitId"));
        if (record == null) {
            record = data.fastestLapByCircuitId.get(getText(source, "id"));
        }
        if (record == null) {
            return;
        }

        JsonNode driver = data.driversById.get(getText(record, "driverId"));
        putIfPresent(target, "lapRecord", getText(record, "time"));
        putIfPresent(target, "fastestLapDriverId", driver == null ? getText(record, "driverId") : getText(driver, "lastName"));
        putIfPresent(target, "fastestLapYear", getText(record, "year"));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Carga y cachea el ZIP oficial de F1DB
     * @return Datos F1DB
     */
    private F1DbData getData() {
        if (dataCache != null) {
            return dataCache;
        }

        try {
            byte[] zipBytes = restTemplate.getForObject(f1dbSplittedJsonUrl, byte[].class);
            F1DbData loadedData = readF1DbZip(zipBytes == null ? new byte[0] : zipBytes);
            if (!loadedData.isEmpty()) {
                dataCache = loadedData;
            }
            return dataCache == null ? F1DbData.empty(objectMapper) : dataCache;
        } catch (RuntimeException ex) {
            return F1DbData.empty(objectMapper);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Lee los JSON necesarios del ZIP de F1DB
     * @param zipBytes ZIP descargado
     * @return Datos preparados
     */
    private F1DbData readF1DbZip(byte[] zipBytes) {
        Map<String, JsonNode> files = new HashMap<>();

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (isNeededFile(name)) {
                    files.put(name, objectMapper.readTree(readEntryBytes(zip)));
                }
            }
        } catch (IOException ex) {
            return F1DbData.empty(objectMapper);
        }

        return F1DbData.from(files, objectMapper);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Lee una entrada del ZIP sin cerrar el stream principal
     * @param zip Stream ZIP
     * @return Bytes de la entrada
     * @throws IOException Error de lectura
     */
    private byte[] readEntryBytes(ZipInputStream zip) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) > 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Filtra los ficheros F1DB necesarios para Calendario
     * @param name Nombre de entrada ZIP
     * @return Resultado
     */
    private boolean isNeededFile(String name) {
        return "f1db-circuits.json".equals(name)
                || "f1db-races.json".equals(name)
                || "f1db-races-fastest-laps.json".equals(name)
                || "f1db-drivers.json".equals(name);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Convierte kilometros de F1DB a metros para unificar formato interno
     * @param node Valor numerico en kilometros
     * @return Metros
     */
    private Integer kilometersToMeters(JsonNode node) {
        if (node == null || node.isNull() || node.asDouble(0) <= 0) {
            return null;
        }
        return (int) Math.round(node.asDouble() * 1000);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Inserta texto solo si existe
     * @param node Nodo destino
     * @param field Campo
     * @param value Valor
     */
    private void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
            node.put(field, value);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Inserta numero solo si es valido
     * @param node Nodo destino
     * @param field Campo
     * @param value Valor
     */
    private void putIfPresent(ObjectNode node, String field, Integer value) {
        if (value != null && value > 0) {
            node.put(field, value);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Lee texto seguro de un nodo JSON
     * @param node Nodo origen
     * @param fieldName Campo
     * @return Texto o null
     */
    private String getText(JsonNode node, String fieldName) {
        JsonNode field = node == null ? null : node.get(fieldName);
        return field == null || field.isNull() ? null : field.asText();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Contenedor interno de datos F1DB indexados para consultas rapidas
     */
    private static final class F1DbData {
        private final ArrayNode circuits;
        private final ArrayNode races;
        private final Map<String, JsonNode> circuitsById;
        private final Map<String, JsonNode> driversById;
        private final Map<String, JsonNode> fastestLapByCircuitId;

        private F1DbData(ArrayNode circuits, ArrayNode races, Map<String, JsonNode> circuitsById,
                Map<String, JsonNode> driversById, Map<String, JsonNode> fastestLapByCircuitId) {
            this.circuits = circuits;
            this.races = races;
            this.circuitsById = circuitsById;
            this.driversById = driversById;
            this.fastestLapByCircuitId = fastestLapByCircuitId;
        }

        private static F1DbData from(Map<String, JsonNode> files, ObjectMapper objectMapper) {
            ArrayNode circuits = asArray(files.get("f1db-circuits.json"), objectMapper);
            ArrayNode races = asArray(files.get("f1db-races.json"), objectMapper);
            ArrayNode fastestLaps = asArray(files.get("f1db-races-fastest-laps.json"), objectMapper);
            ArrayNode drivers = asArray(files.get("f1db-drivers.json"), objectMapper);
            Map<String, JsonNode> circuitsById = indexBy(circuits, "id");
            Map<String, JsonNode> driversById = indexBy(drivers, "id");
            Map<String, JsonNode> racesById = indexBy(races, "id");
            Map<String, JsonNode> fastestLapByCircuitId = buildFastestLapIndex(fastestLaps, racesById);
            return new F1DbData(circuits, races, circuitsById, driversById, fastestLapByCircuitId);
        }

        private static F1DbData empty(ObjectMapper objectMapper) {
            return new F1DbData(objectMapper.createArrayNode(), objectMapper.createArrayNode(),
                    Map.of(), Map.of(), Map.of());
        }

        private boolean isEmpty() {
            return circuits.isEmpty() || races.isEmpty();
        }

        private static ArrayNode asArray(JsonNode node, ObjectMapper objectMapper) {
            return node != null && node.isArray() ? (ArrayNode) node : objectMapper.createArrayNode();
        }

        private static Map<String, JsonNode> indexBy(ArrayNode nodes, String field) {
            Map<String, JsonNode> index = new HashMap<>();
            nodes.forEach(node -> {
                JsonNode value = node.get(field);
                if (value != null && !value.isNull()) {
                    index.put(value.asText(), node);
                }
            });
            return index;
        }

        private static Map<String, JsonNode> buildFastestLapIndex(ArrayNode fastestLaps, Map<String, JsonNode> racesById) {
            Map<String, JsonNode> index = new HashMap<>();
            fastestLaps.forEach(lap -> {
                JsonNode race = racesById.get(lap.path("raceId").asText());
                String circuitId = race == null ? null : race.path("circuitId").asText();
                if (circuitId == null || circuitId.isBlank() || lap.path("timeMillis").asLong(0) <= 0) {
                    return;
                }

                JsonNode current = index.get(circuitId);
                if (current == null || lap.path("timeMillis").asLong() < current.path("timeMillis").asLong()) {
                    index.put(circuitId, lap);
                }
            });
            return index;
        }
    }
}

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.8
 * @created 21-04-2026
 * @modified 12-05-2026
 * @description Servicio para obtener calendario F1 con fallback Jolpica, caché y tolerancia a fallos externos
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class F1ScheduleService {

    private static final int CANCELLED_IMOLA_2023_KEY = -202306;
    private static final int SYNTHETIC_MEETING_FACTOR = 1000;

    private final OpenF1Service openF1Service;
    private final JolpicaService jolpicaService;
    private final F1DbService f1DbService;
    private final ObjectMapper objectMapper;
    private final Map<Integer, ArrayNode> calendarMeetingsCache = new ConcurrentHashMap<>();

    public F1ScheduleService(
            OpenF1Service openF1Service,
            JolpicaService jolpicaService,
            F1DbService f1DbService,
            ObjectMapper objectMapper) {
        this.openF1Service = openF1Service;
        this.jolpicaService = jolpicaService;
        this.f1DbService = f1DbService;
        this.objectMapper = objectMapper;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @description Obtener meetings originales de OpenF1
     * @param year Temporada
     * @return Meetings
     */
    public JsonNode getMeetingsByYear(Integer year) {
        return openF1Service.getMeetings(year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.4
     * @created 28-04-2026
     * @modified 28-04-2026
     * @description Obtener meetings enriquecidos evitando duplicar llamadas
     *              externas en cargas simultaneas
     * @param year Temporada
     * @return Meetings enriquecidos
     */
    public JsonNode getCalendarMeetingsByYear(Integer year) {
        Integer selectedYear = year == null ? LocalDate.now().getYear() : year;
        ArrayNode cachedMeetings = calendarMeetingsCache.get(selectedYear);

        if (cachedMeetings != null) {
            return cachedMeetings.deepCopy();
        }

        synchronized (calendarMeetingsCache) {
            cachedMeetings = calendarMeetingsCache.get(selectedYear);
            if (cachedMeetings != null) {
                return cachedMeetings.deepCopy();
            }

            ArrayNode response = buildCalendarMeetings(selectedYear);
            if (!response.isEmpty()) {
                calendarMeetingsCache.put(selectedYear, response.deepCopy());
            }
            return response;
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 28-04-2026
     * @modified 12-05-2026
     * @description Construye el calendario enriquecido con APIs externas en
     *              paralelo sin romper toda la temporada si una fuente falla
     * @param selectedYear Temporada
     * @return Meetings enriquecidos
     */
    private ArrayNode buildCalendarMeetings(Integer selectedYear) {
        CompletableFuture<ArrayNode> openMeetingsFuture = safeArrayFuture(
                () -> asArrayNode(openF1Service.getMeetings(selectedYear)));
        CompletableFuture<ArrayNode> jolpicaRacesFuture = safeArrayFuture(
                () -> jolpicaService.getRacesByYear(selectedYear));
        CompletableFuture<ArrayNode> f1DbCircuitsFuture = safeArrayFuture(
                () -> f1DbService.getCircuits(selectedYear));

        ArrayNode openMeetings = openMeetingsFuture.join();
        ArrayNode jolpicaRaces = jolpicaRacesFuture.join();
        ArrayNode f1DbCircuits = f1DbCircuitsFuture.join();
        List<JsonNode> enrichedMeetings = new ArrayList<>();

        for (JsonNode meeting : openMeetings) {
            if (isTestingMeeting(meeting)) {
                continue;
            }
            ObjectNode enrichedMeeting = meeting.isObject()
                    ? ((ObjectNode) meeting).deepCopy()
                    : objectMapper.createObjectNode();

            normalizeOpenF1MeetingState(enrichedMeeting);
            enrichMeeting(enrichedMeeting, jolpicaRaces, f1DbCircuits);
            enrichedMeetings.add(enrichedMeeting);
        }

        for (JsonNode jolpicaRace : jolpicaRaces) {
            ObjectNode fallbackMeeting = buildSyntheticMeetingFromJolpicaRace(selectedYear, jolpicaRace, f1DbCircuits);
            if (!containsEquivalentMeeting(enrichedMeetings, fallbackMeeting)) {
                enrichedMeetings.add(fallbackMeeting);
            }
        }

        for (JsonNode cancelledMeeting : getCancelledMeetings(selectedYear, f1DbCircuits)) {
            if (!containsEquivalentMeeting(enrichedMeetings, cancelledMeeting)) {
                enrichedMeetings.add(cancelledMeeting);
            }
        }

        enrichedMeetings.sort(Comparator.comparing(meeting -> parseDate(getText(meeting, "date_start")),
                Comparator.nullsLast(Comparator.naturalOrder())));

        ArrayNode response = objectMapper.createArrayNode();
        enrichedMeetings.forEach(response::add);
        return response;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Ejecuta una fuente externa en paralelo devolviendo array vacío si falla
     * @param supplier Fuente de datos
     * @return Futuro tolerante a errores
     */
    private CompletableFuture<ArrayNode> safeArrayFuture(Supplier<ArrayNode> supplier) {
        return CompletableFuture.supplyAsync(() -> safeArray(supplier));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Ejecuta una fuente de datos sin propagar errores externos al calendario
     * @param supplier Fuente de datos
     * @return Array seguro
     */
    private ArrayNode safeArray(Supplier<ArrayNode> supplier) {
        try {
            ArrayNode result = supplier.get();
            return result == null ? objectMapper.createArrayNode() : result;
        } catch (RuntimeException ex) {
            return objectMapper.createArrayNode();
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Convierte un JsonNode en ArrayNode sin lanzar errores si la fuente cambia de forma
     * @param node Nodo recibido
     * @return Array seguro
     */
    private ArrayNode asArrayNode(JsonNode node) {
        return node != null && node.isArray() ? (ArrayNode) node : objectMapper.createArrayNode();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 21-04-2026
     * @modified 12-05-2026
     * @description Obtener meeting real o sintético con fallback contra calendario enriquecido
     * @param meetingKey Clave del meeting
     * @return Meeting
     */
    public JsonNode getMeetingByKey(Integer meetingKey) {
        if (meetingKey == null) {
            return objectMapper.nullNode();
        }
        if (isSyntheticJolpicaMeetingKey(meetingKey)) {
            int year = getSyntheticMeetingYear(meetingKey);
            for (JsonNode meeting : getCalendarMeetingsByYear(year)) {
                if (meeting.path("meeting_key").asInt() == meetingKey) {
                    return meeting.deepCopy();
                }
            }
            return objectMapper.nullNode();
        }
        ArrayNode meetings = safeArray(() -> asArrayNode(openF1Service.getMeeting(meetingKey)));
        if (!meetings.isEmpty()) {
            return meetings.get(0).deepCopy();
        }

        JsonNode fallbackMeeting = findMeetingInKnownCalendars(meetingKey);
        return fallbackMeeting == null ? objectMapper.nullNode() : fallbackMeeting;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Busca un meeting en calendarios enriquecidos ya cargados o en temporadas recientes
     * @param meetingKey Clave OpenF1
     * @return Meeting enriquecido o null
     */
    private JsonNode findMeetingInKnownCalendars(Integer meetingKey) {
        JsonNode cachedMeeting = findMeetingInCachedCalendars(meetingKey);
        if (cachedMeeting != null) {
            return cachedMeeting;
        }

        int currentYear = LocalDate.now().getYear();
        for (int year = currentYear + 1; year >= 2023; year--) {
            JsonNode meeting = findMeetingInArray(asArrayNode(getCalendarMeetingsByYear(year)), meetingKey);
            if (meeting != null) {
                return meeting;
            }
        }
        return null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Busca un meeting en la caché de calendarios sin nuevas llamadas externas
     * @param meetingKey Clave OpenF1
     * @return Meeting enriquecido o null
     */
    private JsonNode findMeetingInCachedCalendars(Integer meetingKey) {
        for (ArrayNode meetings : calendarMeetingsCache.values()) {
            JsonNode meeting = findMeetingInArray(meetings, meetingKey);
            if (meeting != null) {
                return meeting;
            }
        }
        return null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Busca una clave dentro de un calendario concreto
     * @param meetings   Calendario
     * @param meetingKey Clave OpenF1
     * @return Meeting enriquecido o null
     */
    private JsonNode findMeetingInArray(ArrayNode meetings, Integer meetingKey) {
        for (JsonNode meeting : meetings) {
            if (meeting.path("meeting_key").asInt(Integer.MIN_VALUE) == meetingKey) {
                return meeting.deepCopy();
            }
        }
        return null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.5
     * @created 21-04-2026
     * @modified 04-05-2026
     * @description Obtener sesiones de OpenF1 completadas con Jolpica cuando falta
     *              alguna sesión oficial
     * @param meetingKey Clave del meeting
     * @return Sesiones
     */
    public JsonNode getSessionsByMeeting(Integer meetingKey) {
        if (meetingKey != null && meetingKey.equals(CANCELLED_IMOLA_2023_KEY)) {
            return buildCancelledSessions(LocalDate.of(2023, 5, 21));
        }
        if (meetingKey == null) {
            return objectMapper.createArrayNode();
        }

        if (isSyntheticJolpicaMeetingKey(meetingKey)) {
            return getSyntheticSessionsByMeetingKey(meetingKey);
        }

        JsonNode meeting = getMeetingByKey(meetingKey);
        if (isCancelledMeeting(meeting)) {
            LocalDate raceDate = getLocalDate(meeting, "date_end");
            return buildCancelledSessions(raceDate == null ? LocalDate.of(2023, 5, 21) : raceDate);
        }

        ArrayNode openSessions = (ArrayNode) openF1Service.getSessions(meetingKey);
        ArrayNode jolpicaSessions = getJolpicaSessionsByOpenF1Meeting(meetingKey);
        return mergeSessions(openSessions, jolpicaSessions);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Fusiona OpenF1 y Jolpica conservando session_key real y
     *              completando sesiones ausentes
     * @param openSessions    Sesiones OpenF1
     * @param jolpicaSessions Sesiones Jolpica
     * @return Sesiones ordenadas y sin duplicados
     */
    private ArrayNode mergeSessions(ArrayNode openSessions, ArrayNode jolpicaSessions) {
        List<ObjectNode> merged = new ArrayList<>();

        for (JsonNode session : openSessions) {
            if (session.isObject()) {
                merged.add(((ObjectNode) session).deepCopy());
            }
        }

        for (JsonNode jolpicaSession : jolpicaSessions) {
            if (jolpicaSession.isObject() && !containsEquivalentSession(merged, jolpicaSession)) {
                merged.add(((ObjectNode) jolpicaSession).deepCopy());
            }
        }

        merged.sort(Comparator.comparing(session -> parseDate(getText(session, "date_start")),
                Comparator.nullsLast(Comparator.naturalOrder())));

        ArrayNode response = objectMapper.createArrayNode();
        merged.forEach(response::add);
        return response;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Detecta duplicados por identidad deportiva o por misma hora
     *              oficial
     * @param sessions  Sesiones ya fusionadas
     * @param candidate Sesión candidata
     * @return Resultado
     */
    private boolean containsEquivalentSession(List<ObjectNode> sessions, JsonNode candidate) {
        String candidateIdentity = getSessionIdentity(candidate);
        return sessions.stream().anyMatch(session -> candidateIdentity.equals(getSessionIdentity(session)));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Normaliza nombres de sesiones entre OpenF1 y Jolpica sin
     *              fusionar variantes sprint distintas
     * @param session Sesión
     * @return Identidad normalizada
     */
    private String getSessionIdentity(JsonNode session) {
        String name = normalize(getText(session, "session_name"));
        String type = normalize(getText(session, "session_type"));

        if (name.contains("practice 1") || name.contains("free practice 1")) {
            return "practice-1";
        }
        if (name.contains("practice 2") || name.contains("free practice 2")) {
            return "practice-2";
        }
        if (name.contains("practice 3") || name.contains("free practice 3")) {
            return "practice-3";
        }
        if (name.contains("sprint qualifying")) {
            return "sprint-qualifying";
        }
        if (name.contains("sprint shootout")) {
            return "sprint-shootout";
        }
        if ("sprint".equals(name)) {
            return "sprint";
        }
        if ("qualifying".equals(name)) {
            return "qualifying";
        }
        if ("race".equals(name)) {
            return "race";
        }

        return type + "-" + name;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Reconstruye sesiones desde Jolpica cuando OpenF1 no las publica
     *              para un meeting normal
     * @param meetingKey Clave OpenF1
     * @return Sesiones fallback
     */
    private ArrayNode getJolpicaSessionsByOpenF1Meeting(Integer meetingKey) {
        JsonNode meetingNode = getMeetingByKey(meetingKey);
        if (!meetingNode.isObject()) {
            return objectMapper.createArrayNode();
        }

        ObjectNode meeting = ((ObjectNode) meetingNode).deepCopy();
        Integer year = getMeetingYear(meeting);
        if (year == null) {
            return objectMapper.createArrayNode();
        }

        JsonNode race = findMatchingJolpicaRace(meeting, jolpicaService.getRacesByYear(year));
        return race == null ? objectMapper.createArrayNode() : buildJolpicaSessions(race);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 28-04-2026
     * @description Obtener siguiente meeting usando calendario enriquecido
     * @param year Temporada
     * @return Meeting
     */
    public JsonNode getNextMeeting(Integer year) {
        ArrayNode meetings = (ArrayNode) getCalendarMeetingsByYear(year);
        OffsetDateTime now = OffsetDateTime.now();

        JsonNode bestMeeting = null;
        OffsetDateTime bestDate = null;

        for (JsonNode meeting : meetings) {
            JsonNode dateEndNode = meeting.get("date_end");
            if (dateEndNode == null || dateEndNode.isNull()) {
                continue;
            }

            OffsetDateTime meetingEnd = parseDate(dateEndNode.asText());

            if (meetingEnd != null && meetingEnd.isAfter(now)) {
                JsonNode dateStartNode = meeting.get("date_start");
                OffsetDateTime comparisonDate = dateStartNode != null && !dateStartNode.isNull()
                        ? parseDate(dateStartNode.asText())
                        : meetingEnd;

                if (comparisonDate != null && (bestDate == null || comparisonDate.isBefore(bestDate))) {
                    bestDate = comparisonDate;
                    bestMeeting = meeting;
                }
            }
        }

        return bestMeeting != null ? bestMeeting : objectMapper.nullNode();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 28-04-2026
     * @description Obtener meeting actual o siguiente usando calendario enriquecido
     * @param year Temporada
     * @return Meeting
     */
    public JsonNode getCurrentOrNextMeeting(Integer year) {
        ArrayNode meetings = (ArrayNode) getCalendarMeetingsByYear(year);
        OffsetDateTime now = OffsetDateTime.now();

        JsonNode bestMeeting = null;
        OffsetDateTime bestDate = null;

        for (JsonNode meeting : meetings) {
            OffsetDateTime start = parseDate(getText(meeting, "date_start"));
            OffsetDateTime end = parseDate(getText(meeting, "date_end"));

            if (start == null || end == null) {
                continue;
            }

            boolean isCurrent = !now.isBefore(start) && !now.isAfter(end);
            boolean isFuture = start.isAfter(now);

            if (isCurrent || isFuture) {
                OffsetDateTime comparisonDate = isCurrent ? now : start;

                if (bestDate == null || comparisonDate.isBefore(bestDate)) {
                    bestDate = comparisonDate;
                    bestMeeting = meeting;
                }
            }
        }

        return bestMeeting != null ? bestMeeting : objectMapper.nullNode();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 12-05-2026
     * @description Obtener siguiente sesión con fallback al calendario enriquecido
     * @param year Temporada
     * @return Sesión
     */
    public JsonNode getNextSession(Integer year) {
        OffsetDateTime now = OffsetDateTime.now();
        ArrayNode sessions = safeArray(() -> asArrayNode(openF1Service.getSessionsByYear(year)));
        JsonNode openF1Session = findCurrentOrNextSession(sessions, now);

        if (!openF1Session.isNull()) {
            return openF1Session;
        }

        return findCurrentOrNextSession(buildCalendarSessionsByYear(year), now);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Busca la sesión actual o futura más cercana dentro de un array seguro
     * @param sessions Sesiones disponibles
     * @param now      Fecha de comparación
     * @return Sesión encontrada o null JSON
     */
    private JsonNode findCurrentOrNextSession(ArrayNode sessions, OffsetDateTime now) {
        JsonNode bestSession = null;
        OffsetDateTime bestDate = null;

        for (JsonNode session : sessions) {
            OffsetDateTime start = parseDate(getText(session, "date_start"));
            OffsetDateTime end = parseDate(getText(session, "date_end"));

            if (start == null || end == null) {
                continue;
            }

            boolean isCurrent = !now.isBefore(start) && !now.isAfter(end);
            boolean isFuture = start.isAfter(now);

            if (isCurrent || isFuture) {
                OffsetDateTime comparisonDate = isCurrent ? now : start;

                if (bestDate == null || comparisonDate.isBefore(bestDate)) {
                    bestDate = comparisonDate;
                    bestSession = session;
                }
            }
        }

        return bestSession != null ? bestSession.deepCopy() : objectMapper.nullNode();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Recorre el calendario enriquecido para obtener sesiones cuando OpenF1 no trae listado anual
     * @param year Temporada
     * @return Sesiones construidas desde la mejor fuente disponible
     */
    private ArrayNode buildCalendarSessionsByYear(Integer year) {
        ArrayNode response = objectMapper.createArrayNode();
        for (JsonNode meeting : getCalendarMeetingsByYear(year)) {
            int meetingKey = meeting.path("meeting_key").asInt(0);
            if (meetingKey == 0) {
                continue;
            }
            ArrayNode sessions = safeArray(() -> asArrayNode(getSessionsByMeeting(meetingKey)));
            for (JsonNode session : sessions) {
                ObjectNode row = session.isObject()
                        ? ((ObjectNode) session).deepCopy()
                        : objectMapper.createObjectNode();
                if (!row.hasNonNull("meeting_key")) {
                    row.put("meeting_key", meetingKey);
                }
                putIfMissing(row, "meeting_name", getText(meeting, "meeting_name"));
                putIfMissing(row, "country_name", getText(meeting, "country_name"));
                response.add(row);
            }
        }
        return response;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Enriquece un meeting con datos automaticos de Jolpica y F1DB
     * @param meeting      Meeting editable
     * @param jolpicaRaces Carreras Jolpica
     * @param f1DbCircuits Circuitos F1DB
     */
    private void enrichMeeting(ObjectNode meeting, ArrayNode jolpicaRaces, ArrayNode f1DbCircuits) {
        JsonNode jolpicaRace = isTestingMeeting(meeting) ? null : findMatchingJolpicaRace(meeting, jolpicaRaces);

        if (jolpicaRace != null) {
            JsonNode circuit = jolpicaRace.path("Circuit");
            JsonNode location = circuit.path("Location");
            ArrayNode jolpicaSessions = buildJolpicaSessions(jolpicaRace);

            putIfText(meeting, "jolpica_round", getText(jolpicaRace, "round"));
            putIfText(meeting, "jolpica_race_url", getText(jolpicaRace, "url"));
            putIfText(meeting, "jolpica_circuit_id", getText(circuit, "circuitId"));
            putIfText(meeting, "jolpica_circuit_name", getText(circuit, "circuitName"));
            putIfText(meeting, "jolpica_circuit_url", getText(circuit, "url"));
            putIfText(meeting, "jolpica_locality", getText(location, "locality"));
            putIfText(meeting, "jolpica_country", getText(location, "country"));
            putIfText(meeting, "total_laps", getText(jolpicaRace, "laps"));
            applyMeetingBoundariesFromSessions(meeting, jolpicaSessions);
        }

        JsonNode f1DbCircuit = findMatchingF1DbCircuit(meeting, f1DbCircuits);
        if (f1DbCircuit == null) {
            f1DbCircuit = findFallbackF1DbCircuit(meeting, f1DbCircuits);
        }
        if (f1DbCircuit != null) {
            putIfText(meeting, "f1db_circuit_id", getText(f1DbCircuit, "circuitId"));
            putIfText(meeting, "f1db_grand_prix_id", getText(f1DbCircuit, "grandPrixId"));
            putIfMissing(meeting, "circuit_length", formatCircuitLength(f1DbCircuit.get("circuitLength")));
            putIfMissing(meeting, "circuit_corners", firstText(f1DbCircuit, "numberOfCorners", "corners"));
            putIfMissing(meeting, "circuit_lap_record", formatLapRecord(f1DbCircuit));
            putIfMissing(meeting, "race_distance", formatCircuitLength(f1DbCircuit.get("raceDistance")));
            putIfMissing(meeting, "total_laps", firstText(f1DbCircuit, "numberOfLaps", "laps"));
            applyKnownRaceFacts(meeting, f1DbCircuit);
        }

        putIfMissing(meeting, "circuit_type", inferCircuitTypeFromText(
                getText(meeting, "jolpica_circuit_name") + " " + getText(f1DbCircuit, "circuitType")));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Ajusta rango del GP usando inicio de primera y última sesión
     * @param meeting  Meeting editable
     * @param sessions Sesiones del GP
     */
    private void applyMeetingBoundariesFromSessions(ObjectNode meeting, ArrayNode sessions) {
        OffsetDateTime firstStart = findSessionBoundaryByStart(sessions, true);
        OffsetDateTime lastStart = findSessionBoundaryByStart(sessions, false);

        if (firstStart != null && lastStart != null) {
            meeting.put("date_start", firstStart.toString());
            meeting.put("date_end", lastStart.toString());
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Construye un meeting compatible cuando OpenF1 no devuelve
     *              calendario
     * @param year         Temporada
     * @param race         Carrera Jolpica
     * @param f1DbCircuits Circuitos F1DB
     * @return Meeting sintético
     */
    private ObjectNode buildSyntheticMeetingFromJolpicaRace(Integer year, JsonNode race, ArrayNode f1DbCircuits) {
        int round = parsePositiveInt(getText(race, "round"), 0);
        JsonNode circuit = race.path("Circuit");
        JsonNode location = circuit.path("Location");
        ArrayNode sessions = buildJolpicaSessions(race);
        OffsetDateTime raceStart = parseJolpicaDateTime(race);
        OffsetDateTime start = findSessionBoundary(sessions, true);
        OffsetDateTime end = findSessionBoundaryByStart(sessions, false);

        if (start == null) {
            start = raceStart != null ? raceStart.minusDays(3) : OffsetDateTime.now();
        }
        if (end == null) {
            end = raceStart != null ? raceStart.plusHours(2) : start.plusDays(3);
        }

        ObjectNode meeting = objectMapper.createObjectNode();
        meeting.put("meeting_key", buildSyntheticMeetingKey(year, round));
        meeting.put("meeting_name", getText(race, "raceName"));
        meeting.put("meeting_official_name", getText(race, "raceName"));
        meeting.put("year", year);
        meeting.put("date_start", start.toString());
        meeting.put("date_end", end.toString());
        meeting.put("gmt_offset", "+00:00");
        meeting.put("is_jolpica_fallback", true);
        putIfText(meeting, "location", getText(location, "locality"));
        putIfText(meeting, "country_name", getText(location, "country"));
        putIfText(meeting, "jolpica_round", getText(race, "round"));
        putIfText(meeting, "jolpica_race_url", getText(race, "url"));
        putIfText(meeting, "jolpica_circuit_id", getText(circuit, "circuitId"));
        putIfText(meeting, "jolpica_circuit_name", getText(circuit, "circuitName"));
        putIfText(meeting, "jolpica_circuit_url", getText(circuit, "url"));
        putIfText(meeting, "jolpica_locality", getText(location, "locality"));
        putIfText(meeting, "jolpica_country", getText(location, "country"));
        putIfText(meeting, "circuit_short_name", getText(circuit, "circuitName"));
        enrichMeeting(meeting, objectMapper.createArrayNode(), f1DbCircuits);
        return meeting;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Obtiene sesiones sintéticas de un meeting creado desde Jolpica
     * @param meetingKey Clave sintética
     * @return Sesiones
     */
    private ArrayNode getSyntheticSessionsByMeetingKey(Integer meetingKey) {
        int year = getSyntheticMeetingYear(meetingKey);
        int round = getSyntheticMeetingRound(meetingKey);
        ArrayNode races = jolpicaService.getRacesByYear(year);

        for (JsonNode race : races) {
            if (parsePositiveInt(getText(race, "round"), -1) == round) {
                return buildJolpicaSessions(race);
            }
        }

        return objectMapper.createArrayNode();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Construye sesiones básicas desde Jolpica cuando OpenF1 no tiene
     *              meeting
     * @param race Carrera Jolpica
     * @return Sesiones sintéticas
     */
    private ArrayNode buildJolpicaSessions(JsonNode race) {
        List<ObjectNode> sessions = new ArrayList<>();
        addJolpicaSession(sessions, race.path("FirstPractice"), "Practice 1", "Practice", 1, "11:30:00Z");
        addJolpicaSession(sessions, race.path("SecondPractice"), "Practice 2", "Practice", 1, "15:00:00Z");
        addJolpicaSession(sessions, race.path("ThirdPractice"), "Practice 3", "Practice", 1, "10:30:00Z");
        addJolpicaSession(sessions, race.path("SprintQualifying"), "Sprint Qualifying", "Qualifying", 1, "14:30:00Z");
        addJolpicaSession(sessions, race.path("SprintShootout"), "Sprint Shootout", "Qualifying", 1, "12:00:00Z");
        addJolpicaSession(sessions, race.path("Sprint"), "Sprint", "Sprint", 1, "18:00:00Z");
        addJolpicaSession(sessions, race.path("Qualifying"), "Qualifying", "Qualifying", 1, "16:00:00Z");
        addJolpicaSession(sessions, race, "Race", "Race", 2, "14:00:00Z");
        sessions.sort(Comparator.comparing(session -> parseDate(getText(session, "date_start")),
                Comparator.nullsLast(Comparator.naturalOrder())));
        ArrayNode response = objectMapper.createArrayNode();
        sessions.forEach(response::add);
        return response;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 28-04-2026
     * @modified 12-05-2026
     * @description Añade una sesión sintética si Jolpica expone fecha
     * @param sessions      Lista editable
     * @param source        Nodo de Jolpica
     * @param name          Nombre de sesión
     * @param type          Tipo de sesión
     * @param durationHours Duración aproximada
     */
    private void addJolpicaSession(List<ObjectNode> sessions, JsonNode source, String name, String type,
            int durationHours, String fallbackTime) {
        OffsetDateTime start = parseJolpicaDateTime(source, fallbackTime);

        if (start == null) {
            return;
        }

        ObjectNode session = objectMapper.createObjectNode();
        session.put("session_name", name);
        session.put("session_type", type);
        session.put("date_start", start.toString());
        session.put("date_end", start.plusHours(durationHours).toString());
        session.put("is_jolpica_fallback", true);
        session.put("source", "Jolpica");
        session.put("synthetic_session_id", getSessionIdentity(session));
        session.put("has_official_results", "Race".equals(type));
        sessions.add(session);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Busca primera o última fecha entre sesiones sintéticas
     * @param sessions Sesiones
     * @param earliest Indica si se busca la primera
     * @return Fecha encontrada
     */
    private OffsetDateTime findSessionBoundary(ArrayNode sessions, boolean earliest) {
        OffsetDateTime result = null;

        for (JsonNode session : sessions) {
            OffsetDateTime date = parseDate(getText(session, earliest ? "date_start" : "date_end"));
            if (date != null && (result == null || (earliest ? date.isBefore(result) : date.isAfter(result)))) {
                result = date;
            }
        }

        return result;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Busca primera o última fecha de inicio entre sesiones
     * @param sessions Sesiones
     * @param earliest Indica si se busca la primera
     * @return Fecha encontrada
     */
    private OffsetDateTime findSessionBoundaryByStart(ArrayNode sessions, boolean earliest) {
        OffsetDateTime result = null;

        for (JsonNode session : sessions) {
            OffsetDateTime date = parseDate(getText(session, "date_start"));
            if (date != null && (result == null || (earliest ? date.isBefore(result) : date.isAfter(result)))) {
                result = date;
            }
        }

        return result;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Convierte fecha y hora Jolpica a OffsetDateTime
     * @param node Nodo con date/time
     * @return Fecha parseada
     */
    private OffsetDateTime parseJolpicaDateTime(JsonNode node) {
        return parseJolpicaDateTime(node, "00:00:00Z");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Convierte fecha Jolpica usando una hora por defecto para evitar
     *              que varias sesiones del mismo día se fusionen por error
     * @param node         Nodo con fecha
     * @param fallbackTime Hora de respaldo
     * @return Fecha parseada
     */
    private OffsetDateTime parseJolpicaDateTime(JsonNode node, String fallbackTime) {
        return parseDate(buildJolpicaDateTime(node, fallbackTime));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Construye texto ISO desde campos date/time de Jolpica
     * @param node Nodo con fecha
     * @return Texto ISO
     */
    private String buildJolpicaDateTime(JsonNode node) {
        return buildJolpicaDateTime(node, "00:00:00Z");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Construye texto ISO desde fecha Jolpica usando hora de respaldo
     *              cuando no existe hora oficial
     * @param node         Nodo con fecha
     * @param fallbackTime Hora de respaldo
     * @return Texto ISO
     */
    private String buildJolpicaDateTime(JsonNode node, String fallbackTime) {
        String date = getText(node, "date");
        String time = getText(node, "time");

        if (date == null || date.isBlank()) {
            return null;
        }

        if (time == null || time.isBlank()) {
            time = fallbackTime == null || fallbackTime.isBlank() ? "00:00:00Z" : fallbackTime;
        } else if (!time.endsWith("Z") && !time.matches(".*[+-]\\d{2}:?\\d{2}$")) {
            time += "Z";
        }

        return date + "T" + time;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Crea una clave negativa y estable para meetings sin clave OpenF1
     * @param year  Temporada
     * @param round Ronda Jolpica
     * @return Clave sintética
     */
    private int buildSyntheticMeetingKey(Integer year, int round) {
        return -((year == null ? LocalDate.now().getYear() : year) * SYNTHETIC_MEETING_FACTOR + round);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Indica si una clave procede del fallback Jolpica
     * @param meetingKey Clave
     * @return Resultado
     */
    private boolean isSyntheticJolpicaMeetingKey(Integer meetingKey) {
        return meetingKey != null && Math.abs(meetingKey) >= 1_000_000;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Extrae temporada de una clave sintética
     * @param meetingKey Clave
     * @return Temporada
     */
    private int getSyntheticMeetingYear(Integer meetingKey) {
        return Math.abs(meetingKey) / SYNTHETIC_MEETING_FACTOR;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Extrae ronda de una clave sintética
     * @param meetingKey Clave
     * @return Ronda
     */
    private int getSyntheticMeetingRound(Integer meetingKey) {
        return Math.abs(meetingKey) % SYNTHETIC_MEETING_FACTOR;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Busca la carrera de Jolpica más parecida al meeting de OpenF1
     * @param meeting Meeting
     * @param races   Carreras Jolpica
     * @return Carrera Jolpica o null
     */
    private JsonNode findMatchingJolpicaRace(ObjectNode meeting, ArrayNode races) {
        JsonNode bestRace = null;
        int bestScore = 0;

        for (JsonNode race : races) {
            int score = getRaceMatchScore(meeting, race);
            if (score > bestScore) {
                bestScore = score;
                bestRace = race;
            }
        }

        return bestScore >= 5 ? bestRace : null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Puntua la similitud entre un meeting y una carrera Jolpica
     * @param meeting Meeting OpenF1
     * @param race    Carrera Jolpica
     * @return Puntuacion
     */
    private int getRaceMatchScore(ObjectNode meeting, JsonNode race) {
        JsonNode circuit = race.path("Circuit");
        JsonNode location = circuit.path("Location");
        int score = 0;

        if (isRaceDateInsideMeeting(meeting, race)) {
            score += 8;
        }

        score += textMatchScore(getText(meeting, "country_name"), getText(location, "country"), 3);
        score += textMatchScore(getText(meeting, "meeting_name"), getText(race, "raceName"), 3);
        score += textMatchScore(getText(meeting, "location"), getText(location, "locality"), 3);
        score += textMatchScore(getText(meeting, "circuit_short_name"), getText(circuit, "circuitName"), 3);

        return score;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Busca el circuito de F1DB más parecido al meeting
     * @param meeting  Meeting enriquecido
     * @param circuits Circuitos F1DB
     * @return Circuito o null
     */
    private JsonNode findMatchingF1DbCircuit(ObjectNode meeting, ArrayNode circuits) {
        JsonNode bestCircuit = null;
        int bestScore = 0;

        for (JsonNode circuit : circuits) {
            int score = getCircuitMatchScore(meeting, circuit);
            if (score > bestScore) {
                bestScore = score;
                bestCircuit = circuit;
            }
        }

        return bestScore >= 4 ? bestCircuit : null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 29-04-2026
     * @modified 29-04-2026
     * @description Fallback por localidad/país para meetings especiales como test
     *              de pretemporada
     * @param meeting  Meeting
     * @param circuits Circuitos F1DB
     * @return Circuito o null
     */
    private JsonNode findFallbackF1DbCircuit(ObjectNode meeting, ArrayNode circuits) {
        String source = normalize(getText(meeting, "location") + " " + getText(meeting, "country_name") + " "
                + getText(meeting, "meeting_name") + " " + getText(meeting, "circuit_short_name"));
        JsonNode bestCircuit = null;
        int bestScore = 0;

        for (JsonNode circuit : circuits) {
            String circuitSource = normalize(getText(circuit, "circuitId") + " " + getText(circuit, "circuitName")
                    + " " + getText(circuit, "city") + " " + getText(circuit, "country"));
            String circuitName = normalize(getText(circuit, "circuitName"));
            int score = 0;

            score += textMatchScore(getText(meeting, "location"), getText(circuit, "city"), 5);
            score += textMatchScore(getText(meeting, "country_name"), getText(circuit, "country"), 3);
            score += !circuitName.isBlank() && source.contains(circuitName) ? 5 : 0;

            if ((source.contains("bahrain") || source.contains("sakhir"))
                    && (circuitSource.contains("bahrain") || circuitSource.contains("sakhir"))) {
                score += 10;
            }
            if ((source.contains("jeddah") || source.contains("saudi"))
                    && (circuitSource.contains("jeddah") || circuitSource.contains("saudi"))) {
                score += 10;
            }

            if (score > bestScore) {
                bestScore = score;
                bestCircuit = circuit;
            }
        }

        return bestScore >= 5 ? bestCircuit : null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Puntua coincidencia entre circuito de APIs y meeting
     * @param meeting Meeting
     * @param circuit Circuito F1DB
     * @return Puntuacion
     */
    private int getCircuitMatchScore(ObjectNode meeting, JsonNode circuit) {
        int score = 0;
        score += textMatchScore(getText(meeting, "jolpica_round"), getText(circuit, "round"), 12);
        score += textMatchScore(getText(meeting, "meeting_name"), getText(circuit, "grandPrixId"), 6);
        score += textMatchScore(getText(meeting, "meeting_official_name"), getText(circuit, "grandPrixId"), 6);
        score += textMatchScore(getText(meeting, "jolpica_circuit_id"), getText(circuit, "circuitId"), 8);
        score += textMatchScore(getText(meeting, "jolpica_circuit_name"), getText(circuit, "circuitName"), 6);
        score += textMatchScore(getText(meeting, "circuit_short_name"), getText(circuit, "circuitName"), 5);
        score += textMatchScore(getText(meeting, "location"), getText(circuit, "city"), 4);
        score += textMatchScore(getText(meeting, "country_name"), getText(circuit, "country"), 3);
        score += textMatchScore(getText(meeting, "meeting_name"), getText(circuit, "circuitId"), 4);
        score += textMatchScore(getText(meeting, "meeting_official_name"), getText(circuit, "circuitId"), 4);

        if (normalize(getText(meeting, "jolpica_circuit_url")).equals(normalize(getText(circuit, "url")))) {
            score += 10;
        }

        return score;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Comprueba si la fecha Jolpica cae dentro del rango del meeting
     *              OpenF1
     * @param meeting Meeting
     * @param race    Carrera Jolpica
     * @return Resultado
     */
    private boolean isRaceDateInsideMeeting(ObjectNode meeting, JsonNode race) {
        try {
            LocalDate raceDate = LocalDate.parse(getText(race, "date"));
            OffsetDateTime start = parseDate(getText(meeting, "date_start"));
            OffsetDateTime end = parseDate(getText(meeting, "date_end"));

            return start != null
                    && end != null
                    && !raceDate.isBefore(start.toLocalDate())
                    && !raceDate.isAfter(end.toLocalDate());
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Calcula puntuacion de coincidencia textual
     * @param left   Texto base
     * @param right  Texto candidato
     * @param points Puntos
     * @return Puntuacion
     */
    private int textMatchScore(String left, String right, int points) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);

        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) {
            return 0;
        }

        return normalizedLeft.equals(normalizedRight)
                || normalizedLeft.contains(normalizedRight)
                || normalizedRight.contains(normalizedLeft)
                        ? points
                        : 0;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 28-04-2026
     * @modified 29-04-2026
     * @description Evita duplicar GPs al mezclar OpenF1 y Jolpica
     * @param meetings  Meetings ya cargados
     * @param candidate Meeting candidato
     * @return Resultado
     */
    private boolean containsEquivalentMeeting(List<JsonNode> meetings, JsonNode candidate) {
        String candidateName = normalize(getText(candidate, "meeting_name"));
        String candidateCircuit = normalize(getText(candidate, "circuit_short_name"));
        String candidateCircuitId = normalize(getText(candidate, "jolpica_circuit_id"));
        String candidateCountry = normalize(getText(candidate, "country_name"));
        LocalDate candidateDate = getLocalDate(candidate, "date_start");

        return meetings.stream().anyMatch(meeting -> normalize(getText(meeting, "meeting_name")).equals(candidateName)
                || hasSameCircuit(candidateCircuitId, candidateCircuit, meeting)
                || hasSameCountryAndCloseDate(candidateCountry, candidateDate, meeting));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Comprueba si dos meetings pertenecen al mismo circuito
     * @param candidateCircuitId Circuito Jolpica candidato
     * @param candidateCircuit   Circuito candidato
     * @param meeting            Meeting existente
     * @return Resultado
     */
    private boolean hasSameCircuit(String candidateCircuitId, String candidateCircuit, JsonNode meeting) {
        String existingCircuitId = normalize(getText(meeting, "jolpica_circuit_id"));
        String existingCircuit = normalize(getText(meeting, "circuit_short_name"));
        String existingJolpicaCircuit = normalize(getText(meeting, "jolpica_circuit_name"));

        return (!candidateCircuitId.isBlank() && candidateCircuitId.equals(existingCircuitId))
                || (!candidateCircuit.isBlank()
                        && (candidateCircuit.equals(existingCircuit)
                                || candidateCircuit.equals(existingJolpicaCircuit)
                                || (!existingJolpicaCircuit.isBlank()
                                        && existingJolpicaCircuit.contains(candidateCircuit))
                                || (!existingJolpicaCircuit.isBlank()
                                        && candidateCircuit.contains(existingJolpicaCircuit))));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Detecta duplicados por país y fecha cercana aunque el nombre
     *              comercial cambie
     * @param candidateCountry País candidato
     * @param candidateDate    Fecha candidata
     * @param meeting          Meeting existente
     * @return Resultado
     */
    private boolean hasSameCountryAndCloseDate(String candidateCountry, LocalDate candidateDate, JsonNode meeting) {
        LocalDate existingDate = getLocalDate(meeting, "date_start");
        String existingCountry = normalize(getText(meeting, "country_name"));

        return candidateDate != null
                && existingDate != null
                && !candidateCountry.isBlank()
                && candidateCountry.equals(existingCountry)
                && Math.abs(existingDate.toEpochDay() - candidateDate.toEpochDay()) <= 3;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 28-04-2026
     * @modified 28-04-2026
     * @description Devuelve GP cancelados conocidos que las APIs de calendario no
     *              exponen como meeting completo
     * @param year         Temporada
     * @param f1DbCircuits Circuitos F1DB
     * @return Meetings cancelados
     */
    private List<JsonNode> getCancelledMeetings(Integer year, ArrayNode f1DbCircuits) {
        if (year == null || year != 2023) {
            return List.of();
        }

        LocalDate raceDate = LocalDate.of(2023, 5, 21);
        ObjectNode imola = objectMapper.createObjectNode();
        imola.put("meeting_key", CANCELLED_IMOLA_2023_KEY);
        imola.put("meeting_name", "Emilia Romagna Grand Prix");
        imola.put("meeting_official_name", "Formula 1 Gran Premio del Made in Italy e dell'Emilia-Romagna 2023");
        imola.put("year", 2023);
        imola.put("country_name", "Italy");
        imola.put("country_code", "ITA");
        imola.put("country_flag", "https://flagcdn.com/w40/it.png");
        imola.put("location", "Imola");
        imola.put("circuit_short_name", "Imola");
        imola.put("date_start", raceDate.minusDays(3) + "T22:00:00+00:00");
        imola.put("date_end", raceDate + "T21:59:00+00:00");
        imola.put("gmt_offset", "+02:00");
        imola.put("is_cancelled", true);
        imola.put("cancelled_reason", "Cancelado por las inundaciones en Emilia-Romagna.");
        imola.put("jolpica_circuit_id", "imola");
        imola.put("jolpica_circuit_name", "Imola Circuit");
        imola.put("jolpica_circuit_url", "https://en.wikipedia.org/wiki/Imola_Circuit");
        enrichMeeting(imola, objectMapper.createArrayNode(), f1DbCircuits);
        imola.set("cancelled_sessions", buildCancelledSessions(raceDate));

        return List.of(imola);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 28-04-2026
     * @modified 28-04-2026
     * @description Construye sesiones canceladas desde la fecha planificada de
     *              carrera
     * @param raceDate Fecha planificada de carrera
     * @return Sesiones canceladas
     */
    private ArrayNode buildCancelledSessions(LocalDate raceDate) {
        ArrayNode sessions = objectMapper.createArrayNode();
        addCancelledSession(sessions, "Practice 1", "Practice", raceDate.minusDays(2) + "T11:30:00+00:00",
                raceDate.minusDays(2) + "T12:30:00+00:00");
        addCancelledSession(sessions, "Practice 2", "Practice", raceDate.minusDays(2) + "T15:00:00+00:00",
                raceDate.minusDays(2) + "T16:00:00+00:00");
        addCancelledSession(sessions, "Practice 3", "Practice", raceDate.minusDays(1) + "T10:30:00+00:00",
                raceDate.minusDays(1) + "T11:30:00+00:00");
        addCancelledSession(sessions, "Qualifying", "Qualifying", raceDate.minusDays(1) + "T14:00:00+00:00",
                raceDate.minusDays(1) + "T15:00:00+00:00");
        addCancelledSession(sessions, "Race", "Race", raceDate + "T13:00:00+00:00", raceDate + "T15:00:00+00:00");
        return sessions;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Añade una sesión cancelada al listado sintético
     * @param sessions Listado
     * @param name     Nombre
     * @param type     Tipo
     * @param start    Inicio
     * @param end      Fin
     */
    private void addCancelledSession(ArrayNode sessions, String name, String type, String start, String end) {
        ObjectNode session = objectMapper.createObjectNode();
        session.put("session_name", name);
        session.put("session_type", type);
        session.put("date_start", start);
        session.put("date_end", end);
        session.put("is_cancelled", true);
        sessions.add(session);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 28-04-2026
     * @modified 30-04-2026
     * @description Infiere tipo de circuito a partir de texto público de APIs
     * @param source Texto de entrada
     * @return Tipo de circuito
     */
    private String inferCircuitTypeFromText(String source) {
        String text = normalize(source);

        if (text.contains("street") || text.contains("urban") || text.contains("city")) {
            return "Circuito Urbano";
        }
        if (text.contains("temporary") || text.contains("temporal")) {
            return "Circuito Temporal";
        }
        if (text.contains("permanent") || text.contains("road") || text.contains("race track")
                || text.contains("racing circuit")) {
            return "Circuito Permanente";
        }

        return null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Formatea longitud de circuito en kilometros
     * @param lengthNode Longitud en metros
     * @return Longitud formateada
     */
    private String formatCircuitLength(JsonNode lengthNode) {
        if (lengthNode == null || lengthNode.isNull()) {
            return null;
        }

        double meters = lengthNode.asDouble(0);
        return meters <= 0 ? null : String.format(Locale.ROOT, "%.3f km", meters / 1000).replace(".", ",");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Formatea vuelta rápida desde F1DB
     * @param circuit Circuito F1DB
     * @return Vuelta rápida
     */
    private String formatLapRecord(JsonNode circuit) {
        if (isBahrainCircuit(circuit)) {
            return "1:31.447 - Pedro de la Rosa (2005)";
        }

        String lapRecord = getText(circuit, "lapRecord");

        if (lapRecord == null || lapRecord.isBlank()) {
            return null;
        }

        int firstColon = lapRecord.indexOf(':');
        int lastColon = lapRecord.lastIndexOf(':');
        if (lastColon > firstColon) {
            lapRecord = lapRecord.substring(0, lastColon) + "." + lapRecord.substring(lastColon + 1);
        }

        String driver = humanizeIdentifier(getText(circuit, "fastestLapDriverId"));
        String year = getText(circuit, "fastestLapYear");

        if (driver == null && year == null) {
            return lapRecord;
        }

        return lapRecord
                + (driver == null ? "" : " - " + driver)
                + (year == null ? "" : " (" + year + ")");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Detecta test de pretemporada para no mezclarlo con datos de
     *              carrera de Bahrain GP
     * @param meeting Meeting
     * @return Resultado
     */
    private boolean isTestingMeeting(JsonNode meeting) {
        String name = normalize(getText(meeting, "meeting_name") + " " + getText(meeting, "meeting_official_name"));
        return name.contains("pre season") || name.contains("testing") || name.contains("test");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Marca meetings cancelados que OpenF1 devuelve solo en el nombre
     *              oficial
     * @param meeting Meeting editable
     */
    private void normalizeOpenF1MeetingState(ObjectNode meeting) {
        if (isCancelledMeeting(meeting)) {
            meeting.put("is_cancelled", true);
            putIfMissing(meeting, "cancelled_reason", "Cancelado oficialmente.");
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Detecta meetings cancelados en datos históricos de OpenF1
     * @param meeting Meeting
     * @return Resultado
     */
    private boolean isCancelledMeeting(JsonNode meeting) {
        String name = normalize(getText(meeting, "meeting_name") + " " + getText(meeting, "meeting_official_name"));
        return (meeting != null && meeting.path("is_cancelled").asBoolean(false))
                || name.contains("called off") || name.contains("cancelled") || name.contains("canceled");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Detecta el circuito de Bahrain para corregir la vuelta rápida
     *              oficial de carrera
     * @param circuit Circuito F1DB
     * @return Resultado
     */
    private boolean isBahrainCircuit(JsonNode circuit) {
        String text = normalize(getText(circuit, "circuitId") + " " + getText(circuit, "circuitName") + " "
                + getText(circuit, "grandPrixId") + " " + getText(circuit, "city") + " " + getText(circuit, "country"));
        return text.contains("bahrain") || text.contains("sakhir");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Detecta Jeddah para completar datos cuando la temporada actual
     *              viene incompleta
     * @param circuit Circuito F1DB
     * @return Resultado
     */
    private boolean isJeddahCircuit(JsonNode circuit) {
        String text = normalize(getText(circuit, "circuitId") + " " + getText(circuit, "circuitName") + " "
                + getText(circuit, "grandPrixId") + " " + getText(circuit, "city") + " " + getText(circuit, "country"));
        return text.contains("jeddah") || text.contains("saudi");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Completa datos oficiales conocidos cuando F1DB no los devuelve
     *              en temporadas futuras
     * @param meeting Meeting editable
     * @param circuit Circuito F1DB
     */
    private void applyKnownRaceFacts(ObjectNode meeting, JsonNode circuit) {
        if (isBahrainCircuit(circuit)) {
            putIfMissing(meeting, "circuit_lap_record", "1:31.447 - Pedro de la Rosa (2005)");
            putIfMissing(meeting, "total_laps", "57");
            putIfMissing(meeting, "race_distance", "308,238 km");
        }
        if (isJeddahCircuit(circuit)) {
            putIfMissing(meeting, "circuit_lap_record", "1:30.734 - Lewis Hamilton (2021)");
            putIfMissing(meeting, "total_laps", "50");
            putIfMissing(meeting, "race_distance", "308,450 km");
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Convierte identificadores snake_case a texto legible
     * @param value Identificador
     * @return Texto
     */
    private String humanizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String[] words = value.replace('-', '_').split("_");
        List<String> formattedWords = new ArrayList<>();

        for (String word : words) {
            if (!word.isBlank()) {
                formattedWords.add(word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1));
            }
        }

        return String.join(" ", formattedWords);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Convierte texto numerico a entero con valor por defecto
     * @param value    Texto
     * @param fallback Valor por defecto
     * @return Número
     */
    private int parsePositiveInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Parsear fecha
     * @param value Fecha
     * @return Fecha parseada
     */
    private OffsetDateTime parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : OffsetDateTime.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Extrae temporada del meeting usando campo year o fecha de inicio
     * @param meeting Meeting
     * @return Temporada
     */
    private Integer getMeetingYear(JsonNode meeting) {
        JsonNode yearNode = meeting == null ? null : meeting.get("year");
        if (yearNode != null && yearNode.canConvertToInt()) {
            return yearNode.asInt();
        }

        OffsetDateTime start = parseDate(getText(meeting, "date_start"));
        return start == null ? null : start.getYear();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Extrae fecha local segura de un campo ISO
     * @param node      Nodo
     * @param fieldName Campo
     * @return Fecha local
     */
    private LocalDate getLocalDate(JsonNode node, String fieldName) {
        OffsetDateTime date = parseDate(getText(node, fieldName));
        return date == null ? null : date.toLocalDate();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @modified 28-04-2026
     * @description Obtener texto seguro de un nodo
     * @param node      Nodo
     * @param fieldName Campo
     * @return Texto
     */
    private String getText(JsonNode node, String fieldName) {
        JsonNode field = node == null ? null : node.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 29-04-2026
     * @description Devuelve el primer texto no vacío entre varios campos
     * @param node       Nodo
     * @param fieldNames Campos
     * @return Texto o null
     */
    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = getText(node, fieldName);
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Escribe texto si existe
     * @param node  Nodo editable
     * @param field Campo
     * @param value Valor
     */
    private void putIfText(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 28-04-2026
     * @modified 29-04-2026
     * @description Escribe texto solo cuando el campo está vacío o sin definir
     * @param node  Nodo editable
     * @param field Campo
     * @param value Valor
     */
    private void putIfMissing(ObjectNode node, String field, String value) {
        JsonNode existing = node.get(field);
        if ((existing == null || existing.isNull() || existing.asText().isBlank() || "-".equals(existing.asText()))
                && value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 28-04-2026
     * @description Normaliza texto para comparaciones
     * @param value Texto
     * @return Texto normalizado
     */
    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);

        return normalized
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ñ", "n")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}

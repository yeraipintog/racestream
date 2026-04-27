/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 21-04-2026
 * @description Servicio para obtener datos de F1
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class F1ScheduleService {

    private final OpenF1Service openF1Service;
    private final ObjectMapper objectMapper;

    public F1ScheduleService(OpenF1Service openF1Service, ObjectMapper objectMapper) {
        this.openF1Service = openF1Service;
        this.objectMapper = objectMapper;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener meetings
     * @param year
     * @return
     */
    public JsonNode getMeetingsByYear(Integer year) {
        return openF1Service.getMeetings(year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener meeting
     * @param meetingKey
     * @return
     */
    public JsonNode getMeetingByKey(Integer meetingKey) {
        ArrayNode meetings = (ArrayNode) openF1Service.getMeeting(meetingKey);
        return meetings.isEmpty() ? objectMapper.nullNode() : meetings.get(0);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener sesiones
     * @param meetingKey
     * @return
     */
    public JsonNode getSessionsByMeeting(Integer meetingKey) {
        return openF1Service.getSessions(meetingKey);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener siguiente meeting
     * @param year
     * @return
     */
    public JsonNode getNextMeeting(Integer year) {
        ArrayNode meetings = (ArrayNode) openF1Service.getMeetings(year);
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
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener meeting actual o siguiente
     * @param year
     * @return
     */
    public JsonNode getCurrentOrNextMeeting(Integer year) {
        ArrayNode meetings = (ArrayNode) openF1Service.getMeetings(year);
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
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener siguiente sesión
     * @param year
     * @return
     */
    public JsonNode getNextSession(Integer year) {
        ArrayNode sessions = (ArrayNode) openF1Service.getSessionsByYear(year);
        OffsetDateTime now = OffsetDateTime.now();

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

        return bestSession != null ? bestSession : objectMapper.nullNode();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0
     * @created 21-04-2026
     * @description Parsear fecha
     * @param value
     * @return
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
     * @version 1.0
     * @created 21-04-2026
     * @description Obtener texto
     * @param node
     * @param fieldName
     * @return
     */
    private String getText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : null;
    }
}
/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 12-05-2026
 * @description Tests de sesiones F1 con OpenF1 y fallback Jolpica sin llamadas externas
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class F1ScheduleServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenF1Service openF1Service;
    private JolpicaService jolpicaService;
    private F1DbService f1DbService;
    private F1ScheduleService service;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Prepara mocks limpios para cada prueba
     */
    @BeforeEach
    void setUp() {
        openF1Service = mock(OpenF1Service.class);
        jolpicaService = mock(JolpicaService.class);
        f1DbService = mock(F1DbService.class);
        service = new F1ScheduleService(openF1Service, jolpicaService, f1DbService, objectMapper);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que un GP normal conserva las 5 sesiones esperadas aunque OpenF1 devuelva solo una
     */
    @Test
    void normalGrandPrixUsesJolpicaToCompleteFiveSessions() throws Exception {
        when(openF1Service.getMeeting(123)).thenReturn(array("""
                [{"meeting_key":123,"meeting_name":"Spanish Grand Prix","country_name":"Spain","location":"Montmelo","year":2026,"date_start":"2026-06-12T00:00:00+00:00","date_end":"2026-06-14T23:59:00+00:00"}]
                """));
        when(openF1Service.getSessions(123)).thenReturn(array("""
                [{"session_key":9001,"session_name":"Race","session_type":"Race","date_start":"2026-06-14T13:00:00+00:00","date_end":"2026-06-14T15:00:00+00:00"}]
                """));
        when(jolpicaService.getRacesByYear(2026)).thenReturn(array("""
                [{"round":"10","raceName":"Spanish Grand Prix","date":"2026-06-14","time":"13:00:00Z","Circuit":{"circuitName":"Circuit de Barcelona-Catalunya","Location":{"locality":"Montmelo","country":"Spain"}},"FirstPractice":{"date":"2026-06-12"},"SecondPractice":{"date":"2026-06-12"},"ThirdPractice":{"date":"2026-06-13"},"Qualifying":{"date":"2026-06-13"}}]
                """));

        ArrayNode sessions = (ArrayNode) service.getSessionsByMeeting(123);

        assertThat(sessionNames(sessions)).containsExactly("Practice 1", "Practice 2", "Practice 3", "Qualifying", "Race");
        assertThat(sessions).hasSize(5);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que Sprint Qualifying y Sprint Shootout no se fusionan por deduplicacion
     */
    @Test
    void sprintGrandPrixKeepsSprintQualifyingAndSprintShootoutSeparate() throws Exception {
        when(openF1Service.getMeeting(456)).thenReturn(array("""
                [{"meeting_key":456,"meeting_name":"Sprint Grand Prix","country_name":"Brazil","location":"Sao Paulo","year":2026,"date_start":"2026-11-06T00:00:00+00:00","date_end":"2026-11-08T23:59:00+00:00"}]
                """));
        when(openF1Service.getSessions(456)).thenReturn(objectMapper.createArrayNode());
        when(jolpicaService.getRacesByYear(2026)).thenReturn(array("""
                [{"round":"21","raceName":"Sprint Grand Prix","date":"2026-11-08","time":"17:00:00Z","Circuit":{"circuitName":"Interlagos","Location":{"locality":"Sao Paulo","country":"Brazil"}},"SprintQualifying":{"date":"2026-11-07"},"SprintShootout":{"date":"2026-11-07"},"Sprint":{"date":"2026-11-07"},"Qualifying":{"date":"2026-11-06"}}]
                """));

        ArrayNode sessions = (ArrayNode) service.getSessionsByMeeting(456);

        assertThat(sessionNames(sessions)).contains("Sprint Qualifying", "Sprint Shootout", "Sprint", "Qualifying", "Race");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que un GP antiguo no depende de OpenF1 y usa clave sintetica Jolpica
     */
    @Test
    void oldGrandPrixUsesSyntheticJolpicaSessions() throws Exception {
        int meetingKey = -(1950 * 1000 + 1);
        when(jolpicaService.getRacesByYear(1950)).thenReturn(array("""
                [{"round":"1","raceName":"British Grand Prix","date":"1950-05-13","Circuit":{"circuitName":"Silverstone","Location":{"locality":"Silverstone","country":"UK"}}}]
                """));

        ArrayNode sessions = (ArrayNode) service.getSessionsByMeeting(meetingKey);

        assertThat(sessionNames(sessions)).containsExactly("Race");
        assertThat(sessions.get(0).path("is_jolpica_fallback").asBoolean()).isTrue();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que un GP cancelado mantiene sesiones visibles y marcadas como canceladas
     */
    @Test
    void cancelledGrandPrixKeepsCancelledSessionsVisible() {
        ArrayNode sessions = (ArrayNode) service.getSessionsByMeeting(-202306);

        assertThat(sessions).hasSize(5);
        assertThat(sessions).allMatch((session) -> session.path("is_cancelled").asBoolean(false));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que Jolpica construye calendario cuando OpenF1 falla
     */
    @Test
    void calendarUsesJolpicaWhenOpenF1Fails() throws Exception {
        when(openF1Service.getMeetings(2026)).thenThrow(new RuntimeException("OpenF1 caido"));
        when(jolpicaService.getRacesByYear(2026)).thenReturn(sampleJolpicaRaces());
        when(f1DbService.getCircuits(2026)).thenReturn(objectMapper.createArrayNode());

        ArrayNode meetings = (ArrayNode) service.getCalendarMeetingsByYear(2026);

        assertThat(meetings).isNotEmpty();
        assertThat(meetings.get(0).path("is_jolpica_fallback").asBoolean()).isTrue();
        assertThat(meetings.get(0).path("meeting_name").asText()).isEqualTo("Abu Dhabi Grand Prix");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que OpenF1 sigue construyendo calendario cuando Jolpica falla
     */
    @Test
    void calendarUsesOpenF1WhenJolpicaFails() throws Exception {
        when(openF1Service.getMeetings(2026)).thenReturn(array("""
                [{"meeting_key":321,"meeting_name":"Spanish Grand Prix","country_name":"Spain","location":"Montmelo","year":2026,"date_start":"2026-06-12T00:00:00+00:00","date_end":"2026-06-14T23:59:00+00:00"}]
                """));
        when(jolpicaService.getRacesByYear(2026)).thenThrow(new RuntimeException("Jolpica caido"));
        when(f1DbService.getCircuits(2026)).thenReturn(objectMapper.createArrayNode());

        ArrayNode meetings = (ArrayNode) service.getCalendarMeetingsByYear(2026);

        assertThat(meetings).isNotEmpty();
        assertThat(meetings.get(0).path("meeting_key").asInt()).isEqualTo(321);
        assertThat(meetings.get(0).path("meeting_name").asText()).isEqualTo("Spanish Grand Prix");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que getNextSession usa calendario Jolpica si OpenF1 no trae sesiones anuales
     */
    @Test
    void nextSessionFallsBackToCalendarMeetings() throws Exception {
        when(openF1Service.getSessionsByYear(2026)).thenReturn(objectMapper.createArrayNode());
        when(openF1Service.getMeetings(2026)).thenReturn(objectMapper.createArrayNode());
        when(jolpicaService.getRacesByYear(2026)).thenReturn(sampleJolpicaRaces());
        when(f1DbService.getCircuits(2026)).thenReturn(objectMapper.createArrayNode());

        JsonNode nextSession = service.getNextSession(2026);

        assertThat(nextSession.path("session_name").asText()).isEqualTo("Practice 1");
        assertThat(nextSession.path("meeting_name").asText()).isEqualTo("Abu Dhabi Grand Prix");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que getMeetingByKey recupera un meeting ya enriquecido si OpenF1 devuelve vacio
     */
    @Test
    void meetingByKeyFallsBackToEnrichedCalendar() throws Exception {
        when(openF1Service.getMeetings(2026)).thenReturn(array("""
                [{"meeting_key":654,"meeting_name":"Italian Grand Prix","country_name":"Italy","location":"Monza","year":2026,"date_start":"2026-09-04T00:00:00+00:00","date_end":"2026-09-06T23:59:00+00:00"}]
                """));
        when(openF1Service.getMeeting(654)).thenReturn(objectMapper.createArrayNode());
        when(jolpicaService.getRacesByYear(2026)).thenReturn(objectMapper.createArrayNode());
        when(f1DbService.getCircuits(2026)).thenReturn(objectMapper.createArrayNode());
        service.getCalendarMeetingsByYear(2026);

        JsonNode meeting = service.getMeetingByKey(654);

        assertThat(meeting.path("meeting_key").asInt()).isEqualTo(654);
        assertThat(meeting.path("meeting_name").asText()).isEqualTo("Italian Grand Prix");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Convierte JSON textual en ArrayNode para mocks
     * @param json JSON
     * @return Array JSON
     * @throws Exception Error de parseo
     */
    private ArrayNode array(String json) throws Exception {
        return (ArrayNode) objectMapper.readTree(json);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Devuelve una carrera futura de Jolpica para pruebas de fallback
     * @return Carreras Jolpica
     * @throws Exception Error de parseo
     */
    private ArrayNode sampleJolpicaRaces() throws Exception {
        return array("""
                [{"round":"24","raceName":"Abu Dhabi Grand Prix","date":"2026-12-06","time":"13:00:00Z","Circuit":{"circuitName":"Yas Marina Circuit","Location":{"locality":"Abu Dhabi","country":"UAE"}},"FirstPractice":{"date":"2026-12-04","time":"09:30:00Z"},"SecondPractice":{"date":"2026-12-04","time":"13:00:00Z"},"ThirdPractice":{"date":"2026-12-05","time":"10:30:00Z"},"Qualifying":{"date":"2026-12-05","time":"14:00:00Z"}}]
                """);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Extrae nombres de sesiones para aserciones compactas
     * @param sessions Sesiones
     * @return Nombres
     */
    private List<String> sessionNames(ArrayNode sessions) {
        return sessions.findValuesAsText("session_name");
    }
}

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 12-05-2026
 * @description Tests de Jolpica con RestTemplate mockeado para standings, resultados y participantes historicos
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JolpicaServiceTest {

    private static final String BASE_URL = "https://api.test/ergast/f1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private JolpicaService service;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Prepara cliente HTTP mockeado para evitar internet en tests
     */
    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new JolpicaService(restTemplate, objectMapper);
        ReflectionTestUtils.setField(service, "jolpicaBaseUrl", BASE_URL);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica paginacion generica de standings con limit y offset
     */
    @Test
    void driverStandingsUseLimitAndOffsetUntilTotal() {
        mockJson("/2026/results.json?limit=100&offset=0", mrData("\"total\":\"1\"", "\"RaceTable\":{\"Races\":[{\"round\":\"1\",\"Results\":[]}]}"));
        mockJson("/2026/driverstandings.json?limit=100&offset=0", mrData("\"total\":\"101\"", "\"StandingsTable\":{\"StandingsLists\":[{\"DriverStandings\":[{\"position\":\"1\",\"points\":\"25\",\"wins\":\"1\",\"Driver\":{\"driverId\":\"driver_a\",\"givenName\":\"Driver\",\"familyName\":\"A\"},\"Constructors\":[]}]}]}"));
        mockJson("/2026/driverstandings.json?limit=100&offset=100", mrData("\"total\":\"101\"", "\"StandingsTable\":{\"StandingsLists\":[{\"DriverStandings\":[{\"position\":\"101\",\"points\":\"0\",\"wins\":\"0\",\"Driver\":{\"driverId\":\"driver_b\",\"givenName\":\"Driver\",\"familyName\":\"B\"},\"Constructors\":[]}]}]}"));

        ArrayNode standings = service.getDriverStandingsByYear(2026);

        assertThat(standings).hasSize(2);
        assertThat(standings.findValuesAsText("driverId")).contains("driver_a", "driver_b");
        server.verify();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica reconstruccion de pilotos desde race-results cuando standings llega vacio
     */
    @Test
    void emptyDriverStandingsAreRebuiltFromRaceResults() {
        mockJson("/1950/results.json?limit=100&offset=0", mrData("\"total\":\"2\"", "\"RaceTable\":{\"Races\":[{\"round\":\"1\",\"Results\":[{\"number\":\"2\",\"positionOrder\":\"1\",\"points\":\"8\",\"Driver\":{\"driverId\":\"farina\",\"givenName\":\"Nino\",\"familyName\":\"Farina\"},\"Constructor\":{\"constructorId\":\"alfa\",\"name\":\"Alfa Romeo\"}},{\"number\":\"4\",\"positionOrder\":\"2\",\"points\":\"6\",\"Driver\":{\"driverId\":\"fagioli\",\"givenName\":\"Luigi\",\"familyName\":\"Fagioli\"},\"Constructor\":{\"constructorId\":\"alfa\",\"name\":\"Alfa Romeo\"}}]}]}"));
        mockJson("/1950/driverstandings.json?limit=100&offset=0", mrData("\"total\":\"0\"", "\"StandingsTable\":{\"StandingsLists\":[{\"DriverStandings\":[]}]}"));

        ArrayNode standings = service.getDriverStandingsByYear(1950);

        assertThat(standings).hasSize(2);
        assertThat(standings.get(0).path("Driver").path("driverId").asText()).isEqualTo("farina");
        assertThat(standings.get(0).path("race_count").asInt()).isEqualTo(1);
        assertThat(standings.get(0).path("season_number").asText()).isEqualTo("2");
        assertThat(standings.get(0).path("Constructors")).hasSize(1);
        server.verify();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que race_count de escuderias cuenta carreras unicas y conserva pilotos
     */
    @Test
    void constructorRaceCountDoesNotDuplicateTwoCarsInSameRace() {
        mockJson("/1988/results.json?limit=100&offset=0", mrData("\"total\":\"2\"", "\"RaceTable\":{\"Races\":[{\"round\":\"1\",\"Results\":[{\"positionOrder\":\"1\",\"points\":\"9\",\"Driver\":{\"driverId\":\"senna\",\"givenName\":\"Ayrton\",\"familyName\":\"Senna\"},\"Constructor\":{\"constructorId\":\"mclaren\",\"name\":\"McLaren\"}},{\"positionOrder\":\"2\",\"points\":\"6\",\"Driver\":{\"driverId\":\"prost\",\"givenName\":\"Alain\",\"familyName\":\"Prost\"},\"Constructor\":{\"constructorId\":\"mclaren\",\"name\":\"McLaren\"}}]}]}"));
        mockJson("/1988/constructorstandings.json?limit=100&offset=0", mrData("\"total\":\"0\"", "\"StandingsTable\":{\"StandingsLists\":[{\"ConstructorStandings\":[]}]}"));

        ArrayNode standings = service.getConstructorStandingsByYear(1988);

        assertThat(standings).hasSize(1);
        assertThat(standings.get(0).path("race_count").asInt()).isEqualTo(1);
        assertThat(standings.get(0).path("Drivers")).hasSize(2);
        server.verify();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que una temporada sin datos completos devuelve arrays seguros
     */
    @Test
    void incompleteSeasonReturnsSafeEmptyArrays() {
        mockJson("/2026/results.json?limit=100&offset=0", mrData("\"total\":\"0\"", "\"RaceTable\":{\"Races\":[]}"));
        mockJson("/2026/races.json?limit=100", mrData("\"total\":\"0\"", "\"RaceTable\":{\"Races\":[]}"));
        mockJson("/2026/driverstandings.json?limit=100&offset=0", mrData("\"total\":\"0\"", "\"StandingsTable\":{\"StandingsLists\":[{\"DriverStandings\":[]}]}"));

        ArrayNode standings = service.getDriverStandingsByYear(2026);

        assertThat(standings).isEmpty();
        server.verify();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que una temporada ya cacheada no se sustituye por llamadas posteriores vacias o fallidas
     */
    @Test
    void racesUseLastValidCacheOnFollowingCalls() {
        mockJson("/2025/races.json?limit=100", mrData("\"total\":\"1\"", "\"RaceTable\":{\"Races\":[{\"round\":\"1\",\"raceName\":\"Australian Grand Prix\"}]}"));

        ArrayNode first = service.getRacesByYear(2025);
        ArrayNode second = service.getRacesByYear(2025);

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).path("raceName").asText()).isEqualTo("Australian Grand Prix");
        server.verify();
    }

    @Test
    void completedTitleYearsExcludeCurrentSeason() {
        int currentYear = LocalDate.now().getYear();

        assertThat(service.completedTitleYears(currentYear))
                .contains(1950, currentYear - 1)
                .doesNotContain(currentYear);
    }

    @Test
    void driverTitleCalculationCountsMultipleTitlesAndMissingSeasons() {
        Map<Integer, ArrayNode> standings = new LinkedHashMap<>();
        standings.put(2022, driverStandings("max_verstappen", "Max", "Verstappen"));
        standings.put(2023, objectMapper.createArrayNode());
        standings.put(2024, driverStandings("max_verstappen", "Max", "Verstappen"));

        ArrayNode titles = service.calculateDriverTitlesFromStandings(standings);

        assertThat(titles).hasSize(1);
        assertThat(titles.get(0).path("driverId").asText()).isEqualTo("max_verstappen");
        assertThat(titles.get(0).path("titles").asInt()).isEqualTo(2);
        assertThat(titles.get(0).path("years").findValuesAsText("")).isEmpty();
        assertThat(titles.get(0).path("years").get(0).asInt()).isEqualTo(2022);
        assertThat(titles.get(0).path("years").get(1).asInt()).isEqualTo(2024);
    }

    @Test
    void constructorTitleCalculationCountsMultipleTitlesAndMissingSeasons() {
        Map<Integer, ArrayNode> standings = new LinkedHashMap<>();
        standings.put(2021, constructorStandings("mercedes", "Mercedes"));
        standings.put(2022, objectMapper.createArrayNode());
        standings.put(2023, constructorStandings("red_bull", "Red Bull"));
        standings.put(2024, constructorStandings("red_bull", "Red Bull"));

        ArrayNode titles = service.calculateConstructorTitlesFromStandings(standings);

        assertThat(titles).hasSize(2);
        assertThat(titles.get(0).path("constructorId").asText()).isEqualTo("red_bull");
        assertThat(titles.get(0).path("titles").asInt()).isEqualTo(2);
        assertThat(titles.get(1).path("constructorId").asText()).isEqualTo("mercedes");
        assertThat(titles.get(1).path("titles").asInt()).isEqualTo(1);
    }

    @Test
    void driverHistoricalTitleSeedsKeepKnownChampions() {
        Map<String, ObjectNode> titles = service.buildDriverTitleSeedRows(2025);

        assertThat(titles.get("michaelschumacher").path("titles").asInt()).isEqualTo(7);
        assertThat(titles.get("lewishamilton").path("titles").asInt()).isEqualTo(7);
        assertThat(titles.get("fangio").path("titles").asInt()).isEqualTo(5);
        assertThat(titles.get("alainprost").path("titles").asInt()).isEqualTo(4);
        assertThat(titles.get("sebastianvettel").path("titles").asInt()).isEqualTo(4);
    }

    @Test
    void constructorHistoricalTitleSeedsKeepKnownTeams() {
        Map<String, ObjectNode> titles = service.buildConstructorTitleSeedRows(2025);

        assertThat(titles.get("ferrari").path("titles").asInt()).isEqualTo(16);
        assertThat(titles.get("mclaren").path("titles").asInt()).isEqualTo(9);
        assertThat(titles.get("williams").path("titles").asInt()).isEqualTo(9);
        assertThat(titles.get("mercedes").path("titles").asInt()).isEqualTo(8);
        assertThat(titles.get("redbull").path("titles").asInt()).isEqualTo(6);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Registra una respuesta JSON mockeada
     * @param pathAndQuery Ruta con query
     * @param body         Cuerpo JSON
     */
    private void mockJson(String pathAndQuery, String body) {
        server.expect(requestTo(BASE_URL + pathAndQuery))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private ArrayNode driverStandings(String driverId, String givenName, String familyName) {
        ArrayNode rows = objectMapper.createArrayNode();
        rows.addObject()
                .put("position", "1")
                .put("stable_id", driverId)
                .set("Driver", objectMapper.createObjectNode()
                        .put("driverId", driverId)
                        .put("givenName", givenName)
                        .put("familyName", familyName));
        return rows;
    }

    private ArrayNode constructorStandings(String constructorId, String name) {
        ArrayNode rows = objectMapper.createArrayNode();
        rows.addObject()
                .put("position", "1")
                .put("stable_id", constructorId)
                .set("Constructor", objectMapper.createObjectNode()
                        .put("constructorId", constructorId)
                        .put("name", name));
        return rows;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Construye una respuesta MRData minima
     * @param totalPart Campo total
     * @param bodyPart  Nodo interno
     * @return JSON
     */
    private String mrData(String totalPart, String bodyPart) {
        return "{\"MRData\":{" + totalPart + "," + bodyPart + "}}";
    }
}

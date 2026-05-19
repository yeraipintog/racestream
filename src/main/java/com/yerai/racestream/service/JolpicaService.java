/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.3
 * @created 28-04-2026
 * @modified 13-05-2026
 * @description Servicio para consultar Jolpica F1 con cache por temporada, calendario, clasificaciones y resultados
 * @see https://github.com/jolpica/jolpica-f1
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JolpicaService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int TITLE_BASELINE_LAST_YEAR = 2024;
    private static final TitleSeed[] DRIVER_TITLE_SEEDS = {
            new TitleSeed("farina", "farina", "Giuseppe Farina", 1950),
            new TitleSeed("fangio", "fangio", "Juan Manuel Fangio", 1951, 1954, 1955, 1956, 1957),
            new TitleSeed("ascari", "ascari", "Alberto Ascari", 1952, 1953),
            new TitleSeed("mikehawthorn", "mike_hawthorn", "Mike Hawthorn", 1958),
            new TitleSeed("brabham", "brabham", "Jack Brabham", 1959, 1960, 1966),
            new TitleSeed("phillhill", "phil_hill", "Phil Hill", 1961),
            new TitleSeed("grahamhill", "graham_hill", "Graham Hill", 1962, 1968),
            new TitleSeed("jimclark", "jim_clark", "Jim Clark", 1963, 1965),
            new TitleSeed("johnsurtees", "john_surtees", "John Surtees", 1964),
            new TitleSeed("dennyhulme", "denny_hulme", "Denny Hulme", 1967),
            new TitleSeed("jackiestewart", "jackie_stewart", "Jackie Stewart", 1969, 1971, 1973),
            new TitleSeed("jochenrindt", "jochen_rindt", "Jochen Rindt", 1970),
            new TitleSeed("emersonfittipaldi", "emerson_fittipaldi", "Emerson Fittipaldi", 1972, 1974),
            new TitleSeed("nikilauda", "lauda", "Niki Lauda", 1975, 1977, 1984),
            new TitleSeed("jameshunt", "hunt", "James Hunt", 1976),
            new TitleSeed("marioandretti", "mario_andretti", "Mario Andretti", 1978),
            new TitleSeed("jodyscheckter", "scheckter", "Jody Scheckter", 1979),
            new TitleSeed("alanjones", "alan_jones", "Alan Jones", 1980),
            new TitleSeed("nelsonpiquet", "piquet", "Nelson Piquet", 1981, 1983, 1987),
            new TitleSeed("kekerosberg", "keke_rosberg", "Keke Rosberg", 1982),
            new TitleSeed("alainprost", "prost", "Alain Prost", 1985, 1986, 1989, 1993),
            new TitleSeed("ayrtonsenna", "senna", "Ayrton Senna", 1988, 1990, 1991),
            new TitleSeed("nigelmansell", "mansell", "Nigel Mansell", 1992),
            new TitleSeed("michaelschumacher", "michael_schumacher", "Michael Schumacher", 1994, 1995, 2000, 2001, 2002, 2003, 2004),
            new TitleSeed("damonhill", "damon_hill", "Damon Hill", 1996),
            new TitleSeed("jacquesvilleneuve", "villeneuve", "Jacques Villeneuve", 1997),
            new TitleSeed("mikahakkinen", "hakkinen", "Mika Hakkinen", 1998, 1999),
            new TitleSeed("fernandoalonso", "alonso", "Fernando Alonso", 2005, 2006),
            new TitleSeed("kimiraikkonen", "raikkonen", "Kimi Raikkonen", 2007),
            new TitleSeed("lewishamilton", "hamilton", "Lewis Hamilton", 2008, 2014, 2015, 2017, 2018, 2019, 2020),
            new TitleSeed("jensonbutton", "button", "Jenson Button", 2009),
            new TitleSeed("sebastianvettel", "vettel", "Sebastian Vettel", 2010, 2011, 2012, 2013),
            new TitleSeed("nicorosberg", "rosberg", "Nico Rosberg", 2016),
            new TitleSeed("maxverstappen", "max_verstappen", "Max Verstappen", 2021, 2022, 2023, 2024)
    };
    private static final TitleSeed[] CONSTRUCTOR_TITLE_SEEDS = {
            new TitleSeed("vanwall", "vanwall", "Vanwall", 1958),
            new TitleSeed("cooper", "cooper", "Cooper", 1959, 1960),
            new TitleSeed("ferrari", "ferrari", "Ferrari", 1961, 1964, 1975, 1976, 1977, 1979, 1982, 1983, 1999, 2000, 2001, 2002, 2003, 2004, 2007, 2008),
            new TitleSeed("brm", "brm", "BRM", 1962),
            new TitleSeed("teamlotus", "team_lotus", "Team Lotus", 1963, 1965, 1968, 1970, 1972, 1973, 1978),
            new TitleSeed("brabham", "brabham", "Brabham", 1966, 1967),
            new TitleSeed("matra", "matra", "Matra", 1969),
            new TitleSeed("tyrrell", "tyrrell", "Tyrrell", 1971),
            new TitleSeed("mclaren", "mclaren", "McLaren", 1974, 1984, 1985, 1988, 1989, 1990, 1991, 1998, 2024),
            new TitleSeed("williams", "williams", "Williams", 1980, 1981, 1986, 1987, 1992, 1993, 1994, 1996, 1997),
            new TitleSeed("benetton", "benetton", "Benetton", 1995),
            new TitleSeed("renault", "renault", "Renault", 2005, 2006),
            new TitleSeed("brawn", "brawn", "Brawn", 2009),
            new TitleSeed("redbull", "red_bull", "Red Bull", 2010, 2011, 2012, 2013, 2022, 2023),
            new TitleSeed("mercedes", "mercedes", "Mercedes", 2014, 2015, 2016, 2017, 2018, 2019, 2020, 2021)
    };

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<Integer, ArrayNode> racesCache = new ConcurrentHashMap<>();
    private final Map<Integer, ArrayNode> driverStandingsCache = new ConcurrentHashMap<>();
    private final Map<Integer, ArrayNode> constructorStandingsCache = new ConcurrentHashMap<>();
    private final Map<Integer, ArrayNode> driverTitleStandingsCache = new ConcurrentHashMap<>();
    private final Map<Integer, ArrayNode> constructorTitleStandingsCache = new ConcurrentHashMap<>();
    private final Map<Integer, ArrayNode> raceResultsCache = new ConcurrentHashMap<>();
    private final Map<Integer, ArrayNode> driverTitlesCache = new ConcurrentHashMap<>();
    private final Map<Integer, ArrayNode> constructorTitlesCache = new ConcurrentHashMap<>();
    private volatile boolean driverTitlesComplete;
    private volatile boolean constructorTitlesComplete;

    @Value("${jolpica.api.base-url:https://api.jolpi.ca/ergast/f1}")
    private String jolpicaBaseUrl;

    public JolpicaService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 28-04-2026
     * @modified 13-05-2026
     * @description Obtiene las carreras de una temporada desde Jolpica y reutiliza
     *              cache sin APIs deprecadas
     * @param year Temporada
     * @return Lista de carreras
     */
    public ArrayNode getRacesByYear(Integer year) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        ArrayNode cachedRaces = racesCache.get(selectedYear);
        if (cachedRaces != null) {
            return cachedRaces.deepCopy();
        }

        String url = UriComponentsBuilder
                .fromUriString(jolpicaBaseUrl)
                .pathSegment(String.valueOf(selectedYear), "races.json")
                .queryParam("limit", 100)
                .toUriString();

        ArrayNode lastResult = objectMapper.createArrayNode();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                JsonNode apiResponse = restTemplate.getForObject(url, JsonNode.class);
                JsonNode mrData = apiResponse == null ? null : apiResponse.path("MRData");
                JsonNode races = apiResponse == null
                        ? null
                        : mrData.path("RaceTable").path("Races");

                lastResult = races != null && races.isArray()
                        ? (ArrayNode) races
                        : objectMapper.createArrayNode();
                if (mrData != null && parseInteger(mrData.path("total").asText(), -1) == 0) {
                    return lastResult.deepCopy();
                }
                if (!lastResult.isEmpty()) {
                    racesCache.put(selectedYear, lastResult.deepCopy());
                    return lastResult.deepCopy();
                }
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            } catch (RestClientException ex) {
                if (attempt < MAX_ATTEMPTS) {
                    sleepAfterExternalFailure(ex, attempt);
                }
            }
        }

        return lastResult.deepCopy();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 30-04-2026
     * @modified 13-05-2026
     * @description Obtiene la clasificacion de pilotos desde Jolpica sin cachear
     *              respuestas vacias
     * @param year Temporada
     * @return Clasificacion de pilotos
     */
    public ArrayNode getDriverStandingsByYear(Integer year) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        ArrayNode cachedStandings = driverStandingsCache.get(selectedYear);
        if (cachedStandings != null) {
            ArrayNode copy = cachedStandings.deepCopy();
            enrichDriverStandingsWithTitles(copy);
            return copy;
        }

        ArrayNode standings = buildDriverStandingsByYear(selectedYear);
        enrichDriverStandingsWithTitles(standings);

        if (!standings.isEmpty()) {
            driverStandingsCache.put(selectedYear, standings.deepCopy());
        }
        return standings.deepCopy();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 30-04-2026
     * @modified 13-05-2026
     * @description Obtiene la clasificacion de constructores desde Jolpica sin
     *              cachear respuestas vacias
     * @param year Temporada
     * @return Clasificacion de constructores
     */
    public ArrayNode getConstructorStandingsByYear(Integer year) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        ArrayNode cachedStandings = constructorStandingsCache.get(selectedYear);
        if (cachedStandings != null) {
            ArrayNode copy = cachedStandings.deepCopy();
            enrichConstructorStandingsWithTitles(copy);
            return copy;
        }

        ArrayNode standings = buildConstructorStandingsByYear(selectedYear);
        enrichConstructorStandingsWithTitles(standings);

        if (!standings.isEmpty()) {
            constructorStandingsCache.put(selectedYear, standings.deepCopy());
        }
        return standings.deepCopy();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Construye una clasificacion de pilotos sin enriquecer mundiales para evitar recursividad interna
     * @param selectedYear Temporada
     * @return Clasificacion base de pilotos
     */
    private ArrayNode buildDriverStandingsByYear(Integer selectedYear) {
        ArrayNode raceResults = getRaceResultsByYear(selectedYear);
        ArrayNode standings = getStandings(selectedYear, "driverstandings.json", "DriverStandings");

        if (standings.isEmpty()) {
            return buildDriverStandingsFromRaceResults(raceResults);
        }

        enrichDriverStandingsWithRaceStats(standings, raceResults);
        return standings;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Construye una clasificacion de escuderias sin enriquecer mundiales para evitar recursividad interna
     * @param selectedYear Temporada
     * @return Clasificacion base de constructores
     */
    private ArrayNode buildConstructorStandingsByYear(Integer selectedYear) {
        ArrayNode raceResults = getRaceResultsByYear(selectedYear);
        ArrayNode standings = getStandings(selectedYear, "constructorstandings.json", "ConstructorStandings");

        if (standings.isEmpty()) {
            return buildConstructorStandingsFromRaceResults(raceResults);
        }

        enrichConstructorStandingsWithRaceStats(standings, raceResults);
        return standings;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Devuelve todas las temporadas disponibles de Formula 1 en orden
     *              descendente
     * @return Temporadas disponibles
     */
    public ArrayNode getAvailableSeasons() {
        ArrayNode seasons = objectMapper.createArrayNode();
        for (int year = LocalDate.now().getYear(); year >= 1950; year--) {
            seasons.addObject().put("season", year);
        }
        return seasons;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Calcula mundiales de pilotos con respaldo historico estable y sin cachear resultados parciales como definitivos
     * @return Pilotos campeones con anos de titulo
     */
    public ArrayNode getDriverWorldTitles() {
        int currentYear = LocalDate.now().getYear();
        ArrayNode cachedTitles = driverTitlesCache.get(currentYear);
        if (cachedTitles != null) {
            driverTitlesComplete = true;
            return cachedTitles.deepCopy();
        }

        Map<String, ObjectNode> titleRows = buildDriverTitleSeedRows(currentYear);
        boolean complete = true;
        for (int year = TITLE_BASELINE_LAST_YEAR + 1; year < currentYear; year++) {
            ArrayNode standings = getDriverStandingsForTitles(year);
            if (standings.isEmpty()) {
                complete = false;
                continue;
            }
            addDriverChampionTitle(titleRows, year, standings);
        }
        ArrayNode titles = sortTitleRows(titleRows);
        markTitleRows(titles, complete);
        driverTitlesComplete = complete;
        if (complete && !titles.isEmpty()) {
            driverTitlesCache.put(currentYear, titles.deepCopy());
        }
        return titles.deepCopy();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Calcula mundiales de constructores con respaldo historico estable y sin cachear resultados parciales como definitivos
     * @return Constructores campeones con anos de titulo
     */
    public ArrayNode getConstructorWorldTitles() {
        int currentYear = LocalDate.now().getYear();
        ArrayNode cachedTitles = constructorTitlesCache.get(currentYear);
        if (cachedTitles != null) {
            constructorTitlesComplete = true;
            return cachedTitles.deepCopy();
        }

        Map<String, ObjectNode> titleRows = buildConstructorTitleSeedRows(currentYear);
        boolean complete = true;
        for (int year = TITLE_BASELINE_LAST_YEAR + 1; year < currentYear; year++) {
            if (year < 1958) {
                continue;
            }
            ArrayNode standings = getConstructorStandingsForTitles(year);
            if (standings.isEmpty()) {
                complete = false;
                continue;
            }
            addConstructorChampionTitle(titleRows, year, standings);
        }
        ArrayNode titles = sortTitleRows(titleRows);
        markTitleRows(titles, complete);
        constructorTitlesComplete = complete;
        if (complete && !titles.isEmpty()) {
            constructorTitlesCache.put(currentYear, titles.deepCopy());
        }
        return titles.deepCopy();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Obtiene standings de pilotos para calcular titulos sin disparar resultados de carrera
     * @param year Temporada
     * @return Clasificacion base cacheable
     */
    private ArrayNode getDriverStandingsForTitles(Integer year) {
        ArrayNode cached = driverStandingsCache.get(year);
        if (cached != null) {
            return cached.deepCopy();
        }
        cached = driverTitleStandingsCache.get(year);
        if (cached != null) {
            return cached.deepCopy();
        }
        ArrayNode standings = getStandings(year, "driverstandings.json", "DriverStandings");
        if (!standings.isEmpty()) {
            driverTitleStandingsCache.put(year, standings.deepCopy());
        }
        return standings;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Obtiene standings de constructores para calcular titulos sin disparar resultados de carrera
     * @param year Temporada
     * @return Clasificacion base cacheable
     */
    private ArrayNode getConstructorStandingsForTitles(Integer year) {
        ArrayNode cached = constructorStandingsCache.get(year);
        if (cached != null) {
            return cached.deepCopy();
        }
        cached = constructorTitleStandingsCache.get(year);
        if (cached != null) {
            return cached.deepCopy();
        }
        ArrayNode standings = getStandings(year, "constructorstandings.json", "ConstructorStandings");
        if (!standings.isEmpty()) {
            constructorTitleStandingsCache.put(year, standings.deepCopy());
        }
        return standings;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 04-05-2026
     * @modified 13-05-2026
     * @description Obtiene resultados paginados y usa fallback por ronda para
     *              detalles historicos de pilotos y escuderias
     * @param year Temporada
     * @return Carreras con resultados oficiales
     */
    public ArrayNode getRaceResultsByYear(Integer year) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        ArrayNode cachedResults = raceResultsCache.get(selectedYear);
        if (cachedResults != null) {
            return cachedResults.deepCopy();
        }

        ArrayNode lastResult = objectMapper.createArrayNode();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ArrayNode result = getPagedRaceResults(selectedYear);
                if (result.isEmpty()) {
                    ArrayNode races = getRacesByYear(selectedYear);
                    if (races.isEmpty()) {
                        return result.deepCopy();
                    }
                    result = getRoundRaceResults(selectedYear, races);
                }
                if (!result.isEmpty()) {
                    raceResultsCache.put(selectedYear, result.deepCopy());
                    return result.deepCopy();
                }
                lastResult = result;
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            } catch (RuntimeException ex) {
                if (attempt < MAX_ATTEMPTS) {
                    sleepAfterExternalFailure(ex, attempt);
                }
            }
        }

        return lastResult.deepCopy();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Genera anos cerrados para no contar la temporada en curso
     * @param currentYear Temporada actual
     * @return Anos que ya han finalizado
     */
    List<Integer> completedTitleYears(int currentYear) {
        List<Integer> years = new ArrayList<>();
        for (int year = 1950; year < currentYear; year++) {
            years.add(year);
        }
        return years;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Prepara mundiales historicos de pilotos verificados hasta la ultima temporada estable
     * @param currentYear Temporada actual
     * @return Filas de titulos por piloto
     */
    Map<String, ObjectNode> buildDriverTitleSeedRows(int currentYear) {
        Map<String, ObjectNode> rows = new LinkedHashMap<>();
        for (TitleSeed seed : DRIVER_TITLE_SEEDS) {
            addSeedYears(rows, seed, currentYear, true);
        }
        return rows;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Prepara mundiales historicos de constructores verificados hasta la ultima temporada estable
     * @param currentYear Temporada actual
     * @return Filas de titulos por escuderia
     */
    Map<String, ObjectNode> buildConstructorTitleSeedRows(int currentYear) {
        Map<String, ObjectNode> rows = new LinkedHashMap<>();
        for (TitleSeed seed : CONSTRUCTOR_TITLE_SEEDS) {
            addSeedYears(rows, seed, currentYear, false);
        }
        return rows;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Anade anos de titulo historicos a una fila semilla
     * @param rows Filas agregadas
     * @param seed Datos historicos estables
     * @param currentYear Temporada actual
     * @param driver Indica si la fila corresponde a piloto
     */
    private void addSeedYears(Map<String, ObjectNode> rows, TitleSeed seed, int currentYear, boolean driver) {
        for (int year : seed.years()) {
            if (year >= currentYear) {
                continue;
            }
            ObjectNode row = rows.computeIfAbsent(seed.stableId(), key -> driver
                    ? createDriverTitleSeedRow(seed)
                    : createConstructorTitleSeedRow(seed));
            addTitleYearIfMissing(row, year);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Crea una fila historica de piloto sin depender de una respuesta externa
     * @param seed Datos historicos estables
     * @return Fila editable
     */
    private ObjectNode createDriverTitleSeedRow(TitleSeed seed) {
        ObjectNode row = objectMapper.createObjectNode();
        row.put("stable_id", seed.stableId());
        row.put("driverId", seed.externalId());
        row.put("name", seed.name());
        row.put("titles", 0);
        row.set("years", objectMapper.createArrayNode());
        return row;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Crea una fila historica de constructor sin depender de una respuesta externa
     * @param seed Datos historicos estables
     * @return Fila editable
     */
    private ObjectNode createConstructorTitleSeedRow(TitleSeed seed) {
        ObjectNode row = objectMapper.createObjectNode();
        row.put("stable_id", seed.stableId());
        row.put("constructorId", seed.externalId());
        row.put("name", seed.name());
        row.put("titles", 0);
        row.set("years", objectMapper.createArrayNode());
        return row;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Fusiona el campeon de piloto devuelto por la API para temporadas posteriores al respaldo estable
     * @param titleRows Filas agregadas
     * @param year Temporada
     * @param standings Clasificacion de la temporada
     */
    private void addDriverChampionTitle(Map<String, ObjectNode> titleRows, int year, ArrayNode standings) {
        JsonNode champion = getChampionStanding(standings);
        JsonNode driver = champion == null ? null : champion.path("Driver");
        String stableId = champion == null ? "" : getText(champion, "stable_id");
        if (stableId.isBlank()) {
            stableId = getDriverStableId(driver);
        }
        if (stableId.isBlank()) {
            return;
        }
        String titleStableId = stableId;
        ObjectNode row = titleRows.computeIfAbsent(titleStableId, key -> createDriverTitleRow(titleStableId, driver));
        addTitleYearIfMissing(row, year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Fusiona el campeon constructor devuelto por la API para temporadas posteriores al respaldo estable
     * @param titleRows Filas agregadas
     * @param year Temporada
     * @param standings Clasificacion de la temporada
     */
    private void addConstructorChampionTitle(Map<String, ObjectNode> titleRows, int year, ArrayNode standings) {
        JsonNode champion = getChampionStanding(standings);
        JsonNode constructor = champion == null ? null : champion.path("Constructor");
        String stableId = champion == null ? "" : getText(champion, "stable_id");
        if (stableId.isBlank()) {
            stableId = getConstructorStableId(constructor);
        }
        if (stableId.isBlank()) {
            return;
        }
        String titleStableId = stableId;
        ObjectNode row = titleRows.computeIfAbsent(titleStableId, key -> createConstructorTitleRow(titleStableId, constructor));
        addTitleYearIfMissing(row, year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Cuenta titulos de pilotos a partir de standings ya preparados
     * @param standingsByYear Clasificaciones por temporada cerrada
     * @return Titulos agregados por piloto
     */
    ArrayNode calculateDriverTitlesFromStandings(Map<Integer, ArrayNode> standingsByYear) {
        Map<String, ObjectNode> titleRows = new LinkedHashMap<>();
        standingsByYear.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    JsonNode champion = getChampionStanding(entry.getValue());
                    JsonNode driver = champion == null ? null : champion.path("Driver");
                    String stableId = champion == null ? "" : getText(champion, "stable_id");
                    if (stableId.isBlank()) {
                        stableId = getDriverStableId(driver);
                    }
                    if (stableId.isBlank()) {
                        return;
                    }
                    String titleStableId = stableId;
                    ObjectNode row = titleRows.computeIfAbsent(titleStableId, key -> createDriverTitleRow(titleStableId, driver));
                    addTitleYear(row, entry.getKey());
                });
        return sortTitleRows(titleRows);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Cuenta titulos de constructores a partir de standings ya preparados
     * @param standingsByYear Clasificaciones por temporada cerrada
     * @return Titulos agregados por constructor
     */
    ArrayNode calculateConstructorTitlesFromStandings(Map<Integer, ArrayNode> standingsByYear) {
        Map<String, ObjectNode> titleRows = new LinkedHashMap<>();
        standingsByYear.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    JsonNode champion = getChampionStanding(entry.getValue());
                    JsonNode constructor = champion == null ? null : champion.path("Constructor");
                    String stableId = champion == null ? "" : getText(champion, "stable_id");
                    if (stableId.isBlank()) {
                        stableId = getConstructorStableId(constructor);
                    }
                    if (stableId.isBlank()) {
                        return;
                    }
                    String titleStableId = stableId;
                    ObjectNode row = titleRows.computeIfAbsent(titleStableId, key -> createConstructorTitleRow(titleStableId, constructor));
                    addTitleYear(row, entry.getKey());
                });
        return sortTitleRows(titleRows);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Localiza el primer clasificado de una temporada sin fallar si la API viene vacia
     * @param standings Clasificacion de temporada
     * @return Fila campeona o null
     */
    private JsonNode getChampionStanding(ArrayNode standings) {
        if (standings == null || standings.isEmpty()) {
            return null;
        }
        for (JsonNode standing : standings) {
            if ("1".equals(standing.path("position").asText())) {
                return standing;
            }
        }
        return standings.get(0);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Crea una fila agregada de mundiales de piloto
     * @param stableId Identificador estable
     * @param driver Nodo piloto Jolpica
     * @return Fila editable
     */
    private ObjectNode createDriverTitleRow(String stableId, JsonNode driver) {
        ObjectNode row = objectMapper.createObjectNode();
        String driverId = getText(driver, "driverId");
        row.put("stable_id", stableId);
        row.put("driverId", driverId.isBlank() ? stableId : driverId);
        row.put("name", getDriverName(driver, stableId));
        row.put("titles", 0);
        row.set("years", objectMapper.createArrayNode());
        return row;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Crea una fila agregada de mundiales de constructor
     * @param stableId Identificador estable
     * @param constructor Nodo constructor Jolpica
     * @return Fila editable
     */
    private ObjectNode createConstructorTitleRow(String stableId, JsonNode constructor) {
        ObjectNode row = objectMapper.createObjectNode();
        String constructorId = getText(constructor, "constructorId");
        row.put("stable_id", stableId);
        row.put("constructorId", constructorId.isBlank() ? stableId : constructorId);
        row.put("name", getConstructorName(constructor, stableId));
        row.put("titles", 0);
        row.set("years", objectMapper.createArrayNode());
        return row;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Anade un ano de titulo y actualiza el contador
     * @param row Fila de titulos
     * @param year Temporada ganada
     */
    private void addTitleYear(ObjectNode row, int year) {
        row.put("titles", row.path("titles").asInt(0) + 1);
        ensureArray(row, "years").add(year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Anade un ano de titulo evitando duplicados al mezclar fuentes
     * @param row Fila de titulos
     * @param year Temporada ganada
     */
    private void addTitleYearIfMissing(ObjectNode row, int year) {
        ArrayNode years = ensureArray(row, "years");
        for (JsonNode storedYear : years) {
            if (storedYear.asInt() == year) {
                return;
            }
        }
        addTitleYear(row, year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Ordena mundiales por cantidad y primer ano conseguido
     * @param rows Filas agregadas
     * @return Array ordenado
     */
    private ArrayNode sortTitleRows(Map<String, ObjectNode> rows) {
        List<ObjectNode> sorted = new ArrayList<>(rows.values());
        sorted.sort(Comparator
                .comparingInt((ObjectNode row) -> row.path("titles").asInt()).reversed()
                .thenComparingInt(row -> row.path("years").path(0).asInt(9999)));
        ArrayNode result = objectMapper.createArrayNode();
        sorted.forEach(result::add);
        return result;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Marca si el calculo historico de titulos esta completo o es parcial
     * @param titles Filas de titulos
     * @param complete Indica si todas las temporadas respondieron
     */
    private void markTitleRows(ArrayNode titles, boolean complete) {
        for (JsonNode title : titles) {
            if (title.isObject()) {
                ObjectNode row = (ObjectNode) title;
                row.put("titlesLoaded", complete);
                row.put("partial", !complete);
                row.set("worldTitleYears", row.path("years").deepCopy());
                row.set("worldTitleSeasons", row.path("years").deepCopy());
                row.put("worldTitlesTotal", row.path("titles").asInt());
            }
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Anade totales historicos de mundiales a cada fila de piloto sin devolver ceros falsos si el calculo falla
     * @param standings Clasificacion editable
     */
    private void enrichDriverStandingsWithTitles(ArrayNode standings) {
        if (standings == null || standings.isEmpty()) {
            return;
        }
        ArrayNode titles = getCachedDriverWorldTitles();
        Map<String, JsonNode> titlesByKey = indexDriverTitles(titles);
        for (JsonNode standing : standings) {
            if (!standing.isObject()) {
                continue;
            }
            ObjectNode row = (ObjectNode) standing;
            String stableId = getDriverStableId(row.path("Driver"));
            if (stableId.isBlank()) {
                stableId = getText(row, "stable_id");
            }
            JsonNode title = titlesByKey.get(normalizeValue(stableId));
            if (title == null) {
                title = titlesByKey.get(normalizeValue(getDriverName(row.path("Driver"), stableId)));
            }
            putTitleFields(row, title, driverTitlesComplete);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Anade totales historicos de mundiales a cada fila de escuderia sin devolver ceros falsos si el calculo falla
     * @param standings Clasificacion editable
     */
    private void enrichConstructorStandingsWithTitles(ArrayNode standings) {
        if (standings == null || standings.isEmpty()) {
            return;
        }
        ArrayNode titles = getCachedConstructorWorldTitles();
        Map<String, JsonNode> titlesByKey = indexConstructorTitles(titles);
        for (JsonNode standing : standings) {
            if (!standing.isObject()) {
                continue;
            }
            ObjectNode row = (ObjectNode) standing;
            String stableId = getConstructorStableId(row.path("Constructor"));
            if (stableId.isBlank()) {
                stableId = getText(row, "stable_id");
            }
            JsonNode title = titlesByKey.get(normalizeValue(stableId));
            if (title == null) {
                title = titlesByKey.get(normalizeValue(getConstructorName(row.path("Constructor"), stableId)));
            }
            putTitleFields(row, title, constructorTitlesComplete);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Devuelve mundiales de pilotos ya calculados sin disparar cargas historicas desde standings
     * @return Titulos cacheados o array vacio
     */
    private ArrayNode getCachedDriverWorldTitles() {
        ArrayNode cached = driverTitlesCache.get(LocalDate.now().getYear());
        return cached == null ? objectMapper.createArrayNode() : cached.deepCopy();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Devuelve mundiales de constructores ya calculados sin disparar cargas historicas desde standings
     * @return Titulos cacheados o array vacio
     */
    private ArrayNode getCachedConstructorWorldTitles() {
        ArrayNode cached = constructorTitlesCache.get(LocalDate.now().getYear());
        return cached == null ? objectMapper.createArrayNode() : cached.deepCopy();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Indexa titulos de pilotos por identificadores estables
     * @param titles Filas de titulos
     * @return Indice por clave normalizada
     */
    private Map<String, JsonNode> indexDriverTitles(ArrayNode titles) {
        Map<String, JsonNode> index = new LinkedHashMap<>();
        for (JsonNode title : titles) {
            addTitleIndex(index, title, title.path("driverId").asText(""));
            addTitleIndex(index, title, title.path("stable_id").asText(""));
            addTitleIndex(index, title, title.path("name").asText(""));
        }
        return index;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Indexa titulos de constructores por identificadores estables
     * @param titles Filas de titulos
     * @return Indice por clave normalizada
     */
    private Map<String, JsonNode> indexConstructorTitles(ArrayNode titles) {
        Map<String, JsonNode> index = new LinkedHashMap<>();
        for (JsonNode title : titles) {
            addTitleIndex(index, title, title.path("constructorId").asText(""));
            addTitleIndex(index, title, title.path("stable_id").asText(""));
            addTitleIndex(index, title, title.path("name").asText(""));
        }
        return index;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Inserta una clave normalizada si es utilizable
     * @param index Indice destino
     * @param row Fila de titulos
     * @param value Valor candidato
     */
    private void addTitleIndex(Map<String, JsonNode> index, JsonNode row, String value) {
        String key = normalizeValue(value);
        if (!key.isBlank()) {
            index.putIfAbsent(key, row);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Copia campos de mundiales a una fila de temporada con estado de carga explicito
     * @param row Fila editable
     * @param title Fila historica localizada
     * @param complete Indica si el calculo global esta completo
     */
    private void putTitleFields(ObjectNode row, JsonNode title, boolean complete) {
        if (title != null && title.isObject()) {
            row.put("worldTitlesTotal", title.path("titles").asInt());
            row.set("worldTitleYears", title.path("years").deepCopy());
            row.set("worldTitleSeasons", title.path("years").deepCopy());
            row.put("titlesLoaded", complete);
            row.put("partial", !complete);
            return;
        }
        if (complete) {
            row.put("worldTitlesTotal", 0);
            row.set("worldTitleYears", objectMapper.createArrayNode());
            row.set("worldTitleSeasons", objectMapper.createArrayNode());
            row.put("titlesLoaded", true);
            row.put("partial", false);
            return;
        }
        row.putNull("worldTitlesTotal");
        row.set("worldTitleYears", objectMapper.createArrayNode());
        row.set("worldTitleSeasons", objectMapper.createArrayNode());
        row.put("titlesLoaded", false);
        row.put("partial", true);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 07-05-2026
     * @description Recupera resultados carrera a carrera cuando Jolpica no entrega
     *              la pagina agregada de una temporada
     * @param selectedYear Temporada
     * @return Carreras con resultados oficiales
     */
    private ArrayNode getRoundRaceResults(Integer selectedYear) {
        return getRoundRaceResults(selectedYear, getRacesByYear(selectedYear));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Recupera resultados carrera a carrera reutilizando calendario ya validado
     * @param selectedYear Temporada
     * @param races Carreras oficiales de la temporada
     * @return Carreras con resultados oficiales
     */
    private ArrayNode getRoundRaceResults(Integer selectedYear, ArrayNode races) {
        Map<String, ObjectNode> racesByRound = new LinkedHashMap<>();
        for (JsonNode race : races) {
            String round = race.path("round").asText();
            if (round.isBlank()) {
                continue;
            }
            String url = UriComponentsBuilder
                    .fromUriString(jolpicaBaseUrl)
                    .pathSegment(String.valueOf(selectedYear), round, "results.json")
                    .queryParam("limit", 100)
                    .toUriString();
            try {
                JsonNode apiResponse = restTemplate.getForObject(url, JsonNode.class);
                JsonNode roundRaces = apiResponse == null
                        ? null
                        : apiResponse.path("MRData").path("RaceTable").path("Races");
                if (roundRaces != null && roundRaces.isArray()) {
                    mergeRaceResults(racesByRound, (ArrayNode) roundRaces);
                }
            } catch (RestClientException ex) {
                sleepAfterExternalFailure(ex, 1);
            }
        }
        ArrayNode result = objectMapper.createArrayNode();
        racesByRound.values().forEach(result::add);
        return result;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 04-05-2026
     * @modified 13-05-2026
     * @description Recorre la paginacion de Jolpica y agrupa resultados por carrera
     * @param selectedYear Temporada
     * @return Carreras completas con sus resultados
     */
    private ArrayNode getPagedRaceResults(Integer selectedYear) {
        final int pageSize = 100;
        int offset = 0;
        int total = Integer.MAX_VALUE;
        Map<String, ObjectNode> racesByRound = new LinkedHashMap<>();

        while (offset < total) {
            String url = UriComponentsBuilder
                    .fromUriString(jolpicaBaseUrl)
                    .pathSegment(String.valueOf(selectedYear), "results.json")
                    .queryParam("limit", pageSize)
                    .queryParam("offset", offset)
                    .toUriString();
            JsonNode apiResponse = restTemplate.getForObject(url, JsonNode.class);
            JsonNode mrData = apiResponse == null ? null : apiResponse.path("MRData");
            JsonNode races = mrData == null ? null : mrData.path("RaceTable").path("Races");

            if (mrData != null) {
                total = parseInteger(mrData.path("total").asText(), offset);
            }
            if (races == null || !races.isArray() || races.isEmpty()) {
                if (total > offset + pageSize) {
                    offset += pageSize;
                    continue;
                }
                break;
            }
            mergeRaceResults(racesByRound, (ArrayNode) races);
            offset += pageSize;
        }

        ArrayNode result = objectMapper.createArrayNode();
        racesByRound.values().forEach(result::add);
        return result;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Fusiona carreras repetidas si la paginacion parte sus resultados
     * @param racesByRound Carreras acumuladas
     * @param pageRaces    Carreras de la pagina actual
     */
    private void mergeRaceResults(Map<String, ObjectNode> racesByRound, ArrayNode pageRaces) {
        for (JsonNode race : pageRaces) {
            String round = race.path("round").asText();
            ObjectNode storedRace = racesByRound.computeIfAbsent(round, key -> {
                ObjectNode copy = race.isObject() ? ((ObjectNode) race).deepCopy() : objectMapper.createObjectNode();
                copy.set("Results", objectMapper.createArrayNode());
                return copy;
            });
            ArrayNode storedResults = (ArrayNode) storedRace.path("Results");
            JsonNode pageResults = race.path("Results");
            if (pageResults.isArray()) {
                pageResults.forEach((result) -> storedResults.add(result.deepCopy()));
            }
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Convierte texto numerico de Jolpica sin romper la carga si viene
     *              vacio
     * @param value    Valor original
     * @param fallback Valor por defecto
     * @return Numero convertido
     */
    private int parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 30-04-2026
     * @modified 13-05-2026
     * @description Consulta una tabla de clasificacion Jolpica y reintenta
     *              respuestas vacias temporales recorriendo toda la paginacion
     * @param year     Temporada
     * @param resource Recurso Jolpica
     * @param nodeName Nodo de clasificacion
     * @return Filas de clasificacion
     */
    private ArrayNode getStandings(Integer year, String resource, String nodeName) {
        final int pageSize = 100;
        int offset = 0;
        int total = Integer.MAX_VALUE;
        Map<String, ObjectNode> rows = new LinkedHashMap<>();

        while (offset < total) {
            JsonNode apiResponse = getStandingsPage(year, resource, pageSize, offset);
            JsonNode mrData = apiResponse == null ? null : apiResponse.path("MRData");
            JsonNode standings = mrData == null
                    ? null
                    : mrData.path("StandingsTable").path("StandingsLists").path(0).path(nodeName);

            if (mrData != null) {
                total = parseInteger(mrData.path("total").asText(), offset);
            }
            if (standings == null || !standings.isArray() || standings.isEmpty()) {
                if (total > offset + pageSize) {
                    offset += pageSize;
                    continue;
                }
                break;
            }

            for (JsonNode standing : standings) {
                if (!standing.isObject()) {
                    continue;
                }
                String id = getStandingStableId(standing, nodeName);
                rows.putIfAbsent(id.isBlank() ? "row-" + offset + "-" + rows.size() : id,
                        ((ObjectNode) standing).deepCopy());
            }
            offset += pageSize;
        }

        ArrayNode response = objectMapper.createArrayNode();
        rows.values().forEach(response::add);
        return response;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Recupera una pagina de standings con reintentos controlados
     * @param year     Temporada
     * @param resource Recurso Jolpica
     * @param limit    Tamano de pagina
     * @param offset   Desplazamiento
     * @return Respuesta JSON de Jolpica
     */
    private JsonNode getStandingsPage(Integer year, String resource, int limit, int offset) {
        String url = UriComponentsBuilder
                .fromUriString(jolpicaBaseUrl)
                .pathSegment(String.valueOf(year), resource)
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .toUriString();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return restTemplate.getForObject(url, JsonNode.class);
            } catch (RestClientException ex) {
                if (attempt < MAX_ATTEMPTS) {
                    sleepAfterExternalFailure(ex, attempt);
                }
            }
        }
        return objectMapper.createObjectNode();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Obtiene una identidad estable para no duplicar filas paginadas
     * @param standing Fila de standings
     * @param nodeName Tipo de standings
     * @return Identificador estable
     */
    private String getStandingStableId(JsonNode standing, String nodeName) {
        return "ConstructorStandings".equals(nodeName)
                ? getConstructorStableId(standing.path("Constructor"))
                : getDriverStableId(standing.path("Driver"));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Reconstruye la clasificación de pilotos desde resultados cuando
     *              Jolpica no devuelve standings agregados
     * @param races Carreras con resultados
     * @return Clasificación de pilotos reconstruida
     */
    private ArrayNode buildDriverStandingsFromRaceResults(ArrayNode races) {
        Map<String, ObjectNode> rows = new LinkedHashMap<>();

        for (JsonNode race : races) {
            for (JsonNode result : race.path("Results")) {
                JsonNode driver = result.path("Driver");
                String id = getDriverStableId(driver);

                if (id.isBlank()) {
                    continue;
                }

                ObjectNode row = rows.computeIfAbsent(id, key -> createDriverStandingRow(driver));
                row.put("points", row.path("points").asDouble(0) + parseDouble(result.path("points").asText()));
                row.put("wins", row.path("wins").asInt(0)
                        + (parseInteger(result.path("positionOrder").asText(), 0) == 1 ? 1 : 0));
                row.put("race_count", row.path("race_count").asInt(0) + 1);
                copySeasonNumber(row, result);
                addConstructorIfMissing(ensureArray(row, "Constructors"), result.path("Constructor"));
            }
        }

        return sortDriverRows(rows);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Reconstruye la clasificación de escuderías desde resultados
     *              cuando Jolpica no devuelve standings agregados
     * @param races Carreras con resultados
     * @return Clasificación de escuderías reconstruida
     */
    private ArrayNode buildConstructorStandingsFromRaceResults(ArrayNode races) {
        Map<String, ObjectNode> rows = new LinkedHashMap<>();

        for (JsonNode race : races) {
            Set<String> constructorsInRace = new HashSet<>();

            for (JsonNode result : race.path("Results")) {
                JsonNode constructor = result.path("Constructor");
                String id = getConstructorStableId(constructor);

                if (id.isBlank()) {
                    continue;
                }

                ObjectNode row = rows.computeIfAbsent(id, key -> createConstructorStandingRow(constructor));
                row.put("points", row.path("points").asDouble(0) + parseDouble(result.path("points").asText()));
                row.put("wins", row.path("wins").asInt(0)
                        + (parseInteger(result.path("positionOrder").asText(), 0) == 1 ? 1 : 0));
                addDriverIfMissing(ensureArray(row, "Drivers"), result.path("Driver"));

                if (constructorsInRace.add(id)) {
                    row.put("race_count", row.path("race_count").asInt(0) + 1);
                }
            }
        }

        return sortConstructorRows(rows);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Añade carreras disputadas y número de temporada a standings
     *              oficiales de pilotos
     * @param standings Clasificación oficial
     * @param races     Resultados de carrera
     */
    private void enrichDriverStandingsWithRaceStats(ArrayNode standings, ArrayNode races) {
        Map<String, ObjectNode> rows = new LinkedHashMap<>();

        for (JsonNode standing : standings) {
            if (standing.isObject()) {
                ObjectNode row = (ObjectNode) standing;
                row.put("race_count", 0);
                row.put("stable_id", getDriverStableId(row.path("Driver")));
                rows.put(getDriverStableId(row.path("Driver")), row);
            }
        }

        for (JsonNode race : races) {
            for (JsonNode result : race.path("Results")) {
                ObjectNode row = rows.get(getDriverStableId(result.path("Driver")));

                if (row == null) {
                    continue;
                }

                row.put("race_count", row.path("race_count").asInt(0) + 1);
                copySeasonNumber(row, result);
                addConstructorIfMissing(ensureArray(row, "Constructors"), result.path("Constructor"));
            }
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Añade carreras disputadas a standings oficiales de constructores
     * @param standings Clasificación oficial
     * @param races     Resultados de carrera
     */
    private void enrichConstructorStandingsWithRaceStats(ArrayNode standings, ArrayNode races) {
        Map<String, ObjectNode> rows = new LinkedHashMap<>();

        for (JsonNode standing : standings) {
            if (standing.isObject()) {
                ObjectNode row = (ObjectNode) standing;
                row.put("race_count", 0);
                row.put("stable_id", getConstructorStableId(row.path("Constructor")));
                ensureArray(row, "Drivers");
                rows.put(getConstructorStableId(row.path("Constructor")), row);
            }
        }

        for (JsonNode race : races) {
            Set<String> constructorsInRace = new HashSet<>();

            for (JsonNode result : race.path("Results")) {
                String id = getConstructorStableId(result.path("Constructor"));
                ObjectNode row = rows.get(id);

                if (row != null && constructorsInRace.add(id)) {
                    row.put("race_count", row.path("race_count").asInt(0) + 1);
                }
                if (row != null) {
                    addDriverIfMissing(ensureArray(row, "Drivers"), result.path("Driver"));
                }
            }
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Crea una fila base de piloto para clasificaciones reconstruidas
     * @param driver Piloto
     * @return Fila base
     */
    private ObjectNode createDriverStandingRow(JsonNode driver) {
        ObjectNode row = objectMapper.createObjectNode();
        row.set("Driver", driver.isMissingNode() ? objectMapper.createObjectNode() : driver.deepCopy());
        row.set("Constructors", objectMapper.createArrayNode());
        row.put("stable_id", getDriverStableId(driver));
        row.put("points", 0);
        row.put("wins", 0);
        row.put("race_count", 0);
        return row;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Crea una fila base de escudería para clasificaciones
     *              reconstruidas
     * @param constructor Constructor
     * @return Fila base
     */
    private ObjectNode createConstructorStandingRow(JsonNode constructor) {
        ObjectNode row = objectMapper.createObjectNode();
        row.set("Constructor", constructor.isMissingNode() ? objectMapper.createObjectNode() : constructor.deepCopy());
        row.set("Drivers", objectMapper.createArrayNode());
        row.put("stable_id", getConstructorStableId(constructor));
        row.put("points", 0);
        row.put("wins", 0);
        row.put("race_count", 0);
        return row;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Ordena filas de pilotos por puntos y victorias
     * @param rows Filas
     * @return Array ordenado
     */
    private ArrayNode sortDriverRows(Map<String, ObjectNode> rows) {
        List<ObjectNode> sorted = new ArrayList<>(rows.values());
        sorted.sort(Comparator
                .comparingDouble((ObjectNode row) -> row.path("points").asDouble()).reversed()
                .thenComparing(Comparator.comparingInt((ObjectNode row) -> row.path("wins").asInt()).reversed()));
        return toPositionedArray(sorted);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Ordena filas de escuderías por puntos y victorias
     * @param rows Filas
     * @return Array ordenado
     */
    private ArrayNode sortConstructorRows(Map<String, ObjectNode> rows) {
        List<ObjectNode> sorted = new ArrayList<>(rows.values());
        sorted.sort(Comparator
                .comparingDouble((ObjectNode row) -> row.path("points").asDouble()).reversed()
                .thenComparing(Comparator.comparingInt((ObjectNode row) -> row.path("wins").asInt()).reversed()));
        return toPositionedArray(sorted);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Añade posición ordinal a filas ordenadas
     * @param rows Filas ordenadas
     * @return Array con posición
     */
    private ArrayNode toPositionedArray(List<ObjectNode> rows) {
        ArrayNode response = objectMapper.createArrayNode();

        for (int index = 0; index < rows.size(); index++) {
            ObjectNode row = rows.get(index);
            row.put("position", String.valueOf(index + 1));
            response.add(row);
        }

        return response;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Añade constructor al piloto evitando duplicados
     * @param constructors Constructores del piloto
     * @param constructor  Constructor candidato
     */
    private void addConstructorIfMissing(ArrayNode constructors, JsonNode constructor) {
        String id = getConstructorStableId(constructor);

        if (id.isBlank()) {
            return;
        }

        for (JsonNode stored : constructors) {
            if (id.equals(getConstructorStableId(stored))) {
                return;
            }
        }

        constructors.add(constructor.deepCopy());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Anade piloto a una escuderia evitando duplicados
     * @param drivers Lista de pilotos de la escuderia
     * @param driver  Piloto candidato
     */
    private void addDriverIfMissing(ArrayNode drivers, JsonNode driver) {
        String id = getDriverStableId(driver);

        if (id.isBlank()) {
            return;
        }

        for (JsonNode stored : drivers) {
            if (id.equals(getDriverStableId(stored))) {
                return;
            }
        }

        drivers.add(driver.deepCopy());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Garantiza que un campo sea array antes de enriquecer una fila
     * @param row       Fila editable
     * @param fieldName Campo array
     * @return Array editable
     */
    private ArrayNode ensureArray(ObjectNode row, String fieldName) {
        JsonNode existing = row.get(fieldName);
        if (existing != null && existing.isArray()) {
            return (ArrayNode) existing;
        }

        ArrayNode array = objectMapper.createArrayNode();
        row.set(fieldName, array);
        return array;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Copia el número usado por el piloto en esa temporada desde
     *              resultados oficiales
     * @param row    Fila de piloto
     * @param result Resultado de carrera
     */
    private void copySeasonNumber(ObjectNode row, JsonNode result) {
        String resultNumber = getText(result, "number");
        String number = resultNumber;

        if (!hasUsableNumber(number) && result.path("Driver").isObject()) {
            number = getText(result.path("Driver"), "permanentNumber");
        }

        if (!hasUsableNumber(number)) {
            return;
        }

        if (!row.hasNonNull("season_number") || row.path("season_number").asText().isBlank()) {
            row.put("season_number", number);
        }
        if (!hasUsableNumber(resultNumber)) {
            row.put("fallback_number", number);
        }

        if (row.path("Driver").isObject()) {
            ((ObjectNode) row.path("Driver")).put("seasonNumber", number);
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Comprueba si un numero de piloto procede de una fuente utilizable
     * @param number Numero recibido
     * @return Resultado
     */
    private boolean hasUsableNumber(String number) {
        return number != null && !number.isBlank() && !"\\N".equals(number);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Genera identificador estable para cruzar pilotos entre standings
     *              y resultados
     * @param driver Piloto
     * @return Identificador normalizado
     */
    private String getDriverStableId(JsonNode driver) {
        String id = getText(driver, "driverId");

        if (id != null && !id.isBlank()) {
            return normalizeValue(id);
        }

        String code = getText(driver, "code");

        if (code != null && !code.isBlank()) {
            return normalizeValue(code);
        }

        return normalizeValue((getText(driver, "givenName") + " " + getText(driver, "familyName")).trim());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Genera identificador estable para cruzar escuderías entre
     *              standings y resultados
     * @param constructor Constructor
     * @return Identificador normalizado
     */
    private String getConstructorStableId(JsonNode constructor) {
        String id = getText(constructor, "constructorId");

        if (id != null && !id.isBlank()) {
            return normalizeValue(id);
        }

        return normalizeValue(getText(constructor, "name"));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Lee texto de un JsonNode sin lanzar errores por nulos
     * @param node      Nodo
     * @param fieldName Campo
     * @return Texto
     */
    private String getText(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }

        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Construye nombre visible de piloto con fallback estable
     * @param driver Nodo piloto
     * @param fallback Texto alternativo
     * @return Nombre visible
     */
    private String getDriverName(JsonNode driver, String fallback) {
        String name = (getText(driver, "givenName") + " " + getText(driver, "familyName")).trim();
        if (name.isBlank()) {
            name = getText(driver, "name");
        }
        return name.isBlank() ? fallback : name;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Construye nombre visible de constructor con fallback estable
     * @param constructor Nodo constructor
     * @param fallback Texto alternativo
     * @return Nombre visible
     */
    private String getConstructorName(JsonNode constructor, String fallback) {
        String name = getText(constructor, "name");
        return name.isBlank() ? fallback : name;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Convierte texto decimal de Jolpica sin romper la carga
     * @param value Valor
     * @return Número decimal
     */
    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value == null || value.isBlank() ? "0" : value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Normaliza identificadores para comparaciones internas
     * @param value Texto
     * @return Texto normalizado
     */
    private String normalizeValue(String value) {
        return value == null
                ? ""
                : value.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Aplica una espera mayor si Jolpica limita temporalmente las peticiones
     * @param ex Error externo
     * @param attempt Intento actual
     */
    private void sleepAfterExternalFailure(RuntimeException ex, int attempt) {
        if (ex instanceof HttpStatusCodeException statusException
                && statusException.getStatusCode().value() == 429) {
            sleepMillis(Math.min(1800L * attempt, 6000L));
            return;
        }
        sleepBeforeRetry(attempt);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Espera breve entre reintentos externos
     * @param attempt Intento actual
     */
    private void sleepBeforeRetry(int attempt) {
        sleepMillis(Math.min(450L * attempt, 2500L));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Pausa controlada entre reintentos sin perder la interrupcion del hilo
     * @param millis Milisegundos de espera
     */
    private void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Registro interno para respaldar mundiales historicos estables
     * @param stableId Identificador normalizado
     * @param externalId Identificador de API
     * @param name Nombre visible
     * @param years Temporadas ganadas
     */
    private record TitleSeed(String stableId, String externalId, String name, int... years) {
    }
}

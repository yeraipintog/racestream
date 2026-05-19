/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.1
 * @created 30-04-2026
 * @modified 13-05-2026
 * @description Controlador REST para exponer clasificaciones, temporadas y resultados de Formula 1 desde Jolpica
 */
package com.yerai.racestream.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerai.racestream.service.JolpicaService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/f1/standings")
@CrossOrigin(origins = "*")
public class F1StandingsController {

    private final JolpicaService jolpicaService;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 13-05-2026
     * @description Constructor con servicio Jolpica
     * @param jolpicaService Servicio de datos Jolpica
     */
    public F1StandingsController(JolpicaService jolpicaService) {
        this.jolpicaService = jolpicaService;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Devuelve clasificacion de pilotos
     * @param year Temporada
     * @return Pilotos ordenados por puntos
     */
    @GetMapping("/drivers")
    public JsonNode getDriverStandings(@RequestParam(required = false) Integer year) {
        return jolpicaService.getDriverStandingsByYear(year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 13-05-2026
     * @description Devuelve clasificacion de constructores
     * @param year Temporada
     * @return Constructores ordenados por puntos
     */
    @GetMapping("/constructors")
    public JsonNode getConstructorStandings(@RequestParam(required = false) Integer year) {
        return jolpicaService.getConstructorStandingsByYear(year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 04-05-2026
     * @modified 13-05-2026
     * @description Devuelve las temporadas disponibles para filtros historicos
     * @return Temporadas disponibles
     */
    @GetMapping("/seasons")
    public JsonNode getAvailableSeasons() {
        return jolpicaService.getAvailableSeasons();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 04-05-2026
     * @description Devuelve carreras con resultados para detalles de clasificacion
     * @param year Temporada
     * @return Carreras con resultados oficiales
     */
    @GetMapping("/race-results")
    public JsonNode getRaceResults(@RequestParam(required = false) Integer year) {
        return jolpicaService.getRaceResultsByYear(year);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Devuelve mundiales de pilotos cerrados hasta la temporada anterior
     * @return Pilotos campeones agregados
     */
    @GetMapping("/driver-titles")
    public JsonNode getDriverTitles() {
        return jolpicaService.getDriverWorldTitles();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 13-05-2026
     * @modified 13-05-2026
     * @description Devuelve mundiales de constructores cerrados hasta la temporada anterior
     * @return Constructores campeones agregados
     */
    @GetMapping("/constructor-titles")
    public JsonNode getConstructorTitles() {
        return jolpicaService.getConstructorWorldTitles();
    }
}

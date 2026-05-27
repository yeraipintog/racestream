/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.3.0
 * @created 30-04-2026
 * @modified 27-05-2026
 * @description Controlador REST para exponer clasificaciones, temporadas y
 *              resultados de Fórmula 1 desde Jolpica con bloqueo histórico
 *              para usuarios anónimos
 */
package com.yerai.racestream.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerai.racestream.service.JolpicaService;
import com.yerai.racestream.service.PublicSeasonAccessService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final PublicSeasonAccessService publicSeasonAccessService;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 13-05-2026
     * @description Constructor con servicios de datos Jolpica y acceso por temporada
     * @param jolpicaService Servicio de datos Jolpica
     * @param publicSeasonAccessService Servicio de bloqueo de históricos para invitados
     */
    public F1StandingsController(JolpicaService jolpicaService, PublicSeasonAccessService publicSeasonAccessService) {
        this.jolpicaService = jolpicaService;
        this.publicSeasonAccessService = publicSeasonAccessService;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Devuelve clasificación de pilotos
     * @param year Temporada
     * @param principal Usuario autenticado o anónimo
     * @return Pilotos ordenados por puntos
     */
    @GetMapping("/drivers")
    public JsonNode getDriverStandings(@RequestParam(required = false) Integer year, @AuthenticationPrincipal Object principal) {
        return jolpicaService.getDriverStandingsByYear(publicSeasonAccessService.resolveYear(year, principal));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 13-05-2026
     * @description Devuelve clasificación de constructores
     * @param year Temporada
     * @param principal Usuario autenticado o anónimo
     * @return Constructores ordenados por puntos
     */
    @GetMapping("/constructors")
    public JsonNode getConstructorStandings(@RequestParam(required = false) Integer year, @AuthenticationPrincipal Object principal) {
        return jolpicaService.getConstructorStandingsByYear(publicSeasonAccessService.resolveYear(year, principal));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 04-05-2026
     * @modified 13-05-2026
     * @description Devuelve las temporadas disponibles para filtros históricos
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
     * @description Devuelve carreras con resultados para detalles de clasificación
     * @param year Temporada
     * @param principal Usuario autenticado o anónimo
     * @return Carreras con resultados oficiales
     */
    @GetMapping("/race-results")
    public JsonNode getRaceResults(@RequestParam(required = false) Integer year, @AuthenticationPrincipal Object principal) {
        return jolpicaService.getRaceResultsByYear(publicSeasonAccessService.resolveYear(year, principal));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 27-05-2026
     * @modified 27-05-2026
     * @description Devuelve carreras con resultados de clasificación para sesiones históricas
     * @param year Temporada
     * @param principal Usuario autenticado
     * @return Carreras con resultados de clasificación
     */
    @GetMapping("/qualifying-results")
    public JsonNode getQualifyingResults(@RequestParam(required = false) Integer year, @AuthenticationPrincipal Object principal) {
        return jolpicaService.getQualifyingResultsByYear(publicSeasonAccessService.resolveYear(year, principal));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 27-05-2026
     * @modified 27-05-2026
     * @description Devuelve carreras con resultados sprint para sesiones históricas
     * @param year Temporada
     * @param principal Usuario autenticado
     * @return Carreras con resultados sprint
     */
    @GetMapping("/sprint-results")
    public JsonNode getSprintResults(@RequestParam(required = false) Integer year, @AuthenticationPrincipal Object principal) {
        return jolpicaService.getSprintResultsByYear(publicSeasonAccessService.resolveYear(year, principal));
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

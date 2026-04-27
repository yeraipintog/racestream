/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 07-04-2026
 * @description Controlador de Race
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.entity.Race;
import com.yerai.racestream.service.RaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/races")
@CrossOrigin(origins = "*")
public class RaceController {
    private final RaceService raceService;

    /**
     * @description Constructor de RaceController
     * @param raceService RaceService
     */
    public RaceController(RaceService raceService) {
        this.raceService = raceService;
    }

    /**
     * @description Obtiene todas las carreras
     * @param championshipId Championship id
     * @param circuitId      Circuit id
     * @return Lista de carreras
     */
    @GetMapping
    public List<Race> getAllRaces(@RequestParam(required = false) Long championshipId,
            @RequestParam(required = false) Long circuitId) {
        if (championshipId != null) {
            return raceService.getRacesByChampionship(championshipId);
        }

        if (circuitId != null) {
            return raceService.getRacesByCircuit(circuitId);
        }

        return raceService.getAllRaces();
    }

    /**
     * @description Obtiene una carrera por su ID
     * @param id ID de la carrera
     * @return Carrera
     */
    @GetMapping("/{id}")
    public Race getRaceById(@PathVariable Long id) {
        return raceService.getRaceById(id);
    }

    /**
     * @description Crea una nueva carrera
     * @param race Carrera a crear
     * @return Carrera creada
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Race createRace(@Valid @RequestBody Race race) {
        return raceService.saveRace(race);
    }

    /**
     * @description Actualiza una carrera
     * @param id   ID de la carrera
     * @param race Carrera a actualizar
     * @return Carrera actualizada
     */
    @PutMapping("/{id}")
    public Race updateRace(@PathVariable Long id, @Valid @RequestBody Race race) {
        return raceService.updateRace(id, race);
    }

    /**
     * @description Elimina una carrera
     * @param id ID de la carrera
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRace(@PathVariable Long id) {
        raceService.deleteRace(id);
    }
}
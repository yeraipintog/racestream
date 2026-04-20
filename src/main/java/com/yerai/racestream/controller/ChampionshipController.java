/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1
 * @created 09-03-2026
 * @modified 07-04-2026
 * @description Controlador de Championship
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.entity.Championship;
import com.yerai.racestream.service.ChampionshipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/championships")
@CrossOrigin(origins = "*")
public class ChampionshipController {
    private final ChampionshipService championshipService;

    /**
     * @description Constructor de ChampionshipController
     * @param championshipService ChampionshipService
     */
    public ChampionshipController(ChampionshipService championshipService) {
        this.championshipService = championshipService;
    }

    /**
     * @description Obtiene todos los campeonatos
     * @return Lista de campeonatos
     */
    @GetMapping
    public List<Championship> getAllChampionships() {
        return championshipService.getAllChampionships();
    }

    /**
     * @description Obtiene un campeonato por ID
     * @param id Championship id
     * @return Championship
     */
    @GetMapping("/{id}")
    public Championship getChampionshipById(@PathVariable Long id) {
        return championshipService.getChampionshipById(id);
    }

    /**
     * @description Crea un nuevo campeonato
     * @param championship Championship
     * @return Championship creado
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Championship createChampionship(@Valid @RequestBody Championship championship) {
        return championshipService.saveChampionship(championship);
    }

    /**
     * @description Actualiza un campeonato
     * @param id                  Championship id
     * @param championshipDetails Championship details
     * @return Championship actualizado
     */
    @PutMapping("/{id}")
    public Championship updateChampionship(@PathVariable Long id, @Valid @RequestBody Championship championship) {
        return championshipService.updateChampionship(id, championship);
    }

    /**
     * @description Elimina un campeonato
     * @param id Championship id
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChampionship(@PathVariable Long id) {
        championshipService.deleteChampionship(id);
    }
}
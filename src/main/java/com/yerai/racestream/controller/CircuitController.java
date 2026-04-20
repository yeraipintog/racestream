/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 07-04-2026
 * @description Controlador de Circuit
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.entity.Circuit;
import com.yerai.racestream.service.CircuitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/circuits")
@CrossOrigin(origins = "*")
public class CircuitController {
    private final CircuitService circuitService;

    /**
     * @description Constructor de CircuitController
     * @param circuitService CircuitService
     */
    public CircuitController(CircuitService circuitService) {
        this.circuitService = circuitService;
    }

    /**
     * @description Obtiene todos los circuitos
     * @param championshipId Championship id
     * @return Lista de circuitos
     */
    @GetMapping
    public List<Circuit> getAllCircuits(@RequestParam(required = false) Long championshipId) {
        return circuitService.getAllCircuits();
    }

    /**
     * @description Obtiene un circuito por ID
     * @param id Circuit id
     * @return Circuit
     */
    @GetMapping("/{id}")
    public Circuit getCircuitById(@PathVariable Long id) {
        return circuitService.getCircuitById(id);
    }

    /**
     * @description Crea un circuito
     * @param circuit Circuit
     * @return Circuit creado
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Circuit createCircuit(@Valid @RequestBody Circuit circuit) {
        return circuitService.saveCircuit(circuit);
    }

    /**
     * @description Actualiza un circuito
     * @param id      Circuit id
     * @param circuit Circuit details
     * @return Circuit actualizado
     */
    @PutMapping("/{id}")
    public Circuit updateCircuit(@PathVariable Long id, @Valid @RequestBody Circuit circuit) {
        return circuitService.updateCircuit(id, circuit);
    }

    /**
     * @description Elimina un circuito
     * @param id Circuit id
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCircuit(@PathVariable Long id) {
        circuitService.deleteCircuit(id);
    }
}
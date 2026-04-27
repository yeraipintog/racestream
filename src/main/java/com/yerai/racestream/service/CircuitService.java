/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 07-04-2026
 * @description Servicio de Circuit
 */
package com.yerai.racestream.service;

import com.yerai.racestream.entity.Circuit;
import com.yerai.racestream.exception.ResourceNotFoundException;
import com.yerai.racestream.repository.CircuitRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CircuitService {
    private final CircuitRepository circuitRepository;

    /**
     * @description Constructor de CircuitService
     * @param circuitRepository CircuitRepository
     */
    public CircuitService(CircuitRepository circuitRepository) {
        this.circuitRepository = circuitRepository;
    }

    /**
     * @description Obtiene todos los circuitos
     * @return Lista de circuitos
     */
    public List<Circuit> getAllCircuits() {
        return circuitRepository.findAll();
    }

    /**
     * @description Obtiene un circuito por ID
     * @param id Circuit id
     * @return Circuit
     */
    public Circuit getCircuitById(Long id) {
        return circuitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Circuito no encontrado con id: " + id));
    }

    /**
     * @description Crea un circuito
     * @param circuit Circuit
     * @return Circuit creado
     */
    public Circuit saveCircuit(Circuit circuit) {
        return circuitRepository.save(circuit);
    }

    /**
     * @description Actualiza un circuito
     * @param id             Circuit id
     * @param circuitDetails Circuit details
     * @return Circuit updated
     */
    public Circuit updateCircuit(Long id, Circuit circuitDetails) {
        Circuit circuit = getCircuitById(id);

        circuit.setName(circuitDetails.getName());
        circuit.setLocation(circuitDetails.getLocation());
        circuit.setLengthKm(circuitDetails.getLengthKm());

        return circuitRepository.save(circuit);
    }

    /**
     * @description Elimina un circuito
     * @param id Circuit id
     */
    public void deleteCircuit(Long id) {
        Circuit circuit = getCircuitById(id);
        circuitRepository.delete(circuit);
    }
}
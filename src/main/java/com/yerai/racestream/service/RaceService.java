/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 07-04-2026
 * @description Servicio de Race
 */
package com.yerai.racestream.service;

import com.yerai.racestream.entity.Championship;
import com.yerai.racestream.entity.Circuit;
import com.yerai.racestream.entity.Race;
import com.yerai.racestream.exception.ResourceNotFoundException;
import com.yerai.racestream.repository.ChampionshipRepository;
import com.yerai.racestream.repository.CircuitRepository;
import com.yerai.racestream.repository.RaceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RaceService {
    private final RaceRepository raceRepository;
    private final ChampionshipRepository championshipRepository;
    private final CircuitRepository circuitRepository;

    /**
     * @description Constructor de RaceService
     * @param raceRepository         ChampionshipRepository
     * @param championshipRepository ChampionshipRepository
     * @param circuitRepository      CircuitRepository
     */
    public RaceService(RaceRepository raceRepository,
            ChampionshipRepository championshipRepository,
            CircuitRepository circuitRepository) {
        this.raceRepository = raceRepository;
        this.championshipRepository = championshipRepository;
        this.circuitRepository = circuitRepository;
    }

    /**
     * @description Obtiene todas las carreras
     * @return Lista de carreras
     */
    public List<Race> getAllRaces() {
        return raceRepository.findAll();
    }

    /**
     * @description Obtiene una carrera por ID
     * @param id Race id
     * @return Race
     */
    public Race getRaceById(Long id) {
        return raceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + id));
    }

    /**
     * @description Obtiene carreras por campeonato
     * @param championshipId Championship id
     * @return Lista de carreras
     */
    public List<Race> getRacesByChampionship(Long championshipId) {
        return raceRepository.findByChampionshipId(championshipId);
    }

    /**
     * @description Obtiene carreras por circuito
     * @param circuitId Circuit id
     * @return Lista de carreras
     */
    public List<Race> getRacesByCircuit(Long circuitId) {
        return raceRepository.findByCircuitId(circuitId);
    }

    /**
     * @description Guarda una carrera
     * @param race Race
     * @return Race guardada
     */
    public Race saveRace(Race race) {
        Long championshipId = race.getChampionship().getId();
        Long circuitId = race.getCircuit().getId();

        Championship championship = championshipRepository.findById(championshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Campeonato no encontrado con id: " + championshipId));

        Circuit circuit = circuitRepository.findById(circuitId)
                .orElseThrow(() -> new ResourceNotFoundException("Circuito no encontrado con id: " + circuitId));

        race.setChampionship(championship);
        race.setCircuit(circuit);

        return raceRepository.save(race);
    }

    /**
     * @description Actualiza una carrera
     * @param id          Race id
     * @param raceDetails Race details
     * @return Race actualizada
     */
    public Race updateRace(Long id, Race raceDetails) {
        Race race = getRaceById(id);

        Long championshipId = raceDetails.getChampionship().getId();
        Long circuitId = raceDetails.getCircuit().getId();

        Championship championship = championshipRepository.findById(championshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Campeonato no encontrado con id: " + championshipId));

        Circuit circuit = circuitRepository.findById(circuitId)
                .orElseThrow(() -> new ResourceNotFoundException("Circuito no encontrado con id: " + circuitId));

        race.setName(raceDetails.getName());
        race.setRaceDate(raceDetails.getRaceDate());
        race.setRoundNumber(raceDetails.getRoundNumber());
        race.setSeason(raceDetails.getSeason());
        race.setChampionship(championship);
        race.setCircuit(circuit);

        return raceRepository.save(race);
    }

    /**
     * @description Elimina una carrera
     * @param id Race id
     */
    public void deleteRace(Long id) {
        Race race = getRaceById(id);
        raceRepository.delete(race);
    }
}
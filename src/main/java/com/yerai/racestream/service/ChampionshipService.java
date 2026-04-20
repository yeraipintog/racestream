/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1
 * @created 09-03-2026
 * @modified 07-04-2026
 * @description Servicio de Championship
 */
package com.yerai.racestream.service;

import com.yerai.racestream.entity.Championship;
import com.yerai.racestream.exception.ResourceNotFoundException;
import com.yerai.racestream.repository.ChampionshipRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChampionshipService {
    private final ChampionshipRepository championshipRepository;

    /**
     * @description Constructor de ChampionshipService
     * @param championshipRepository ChampionshipRepository
     */
    public ChampionshipService(ChampionshipRepository championshipRepository) {
        this.championshipRepository = championshipRepository;
    }

    /**
     * @description Obtiene todos los campeonatos
     * @return Lista de campeonatos
     */
    public List<Championship> getAllChampionships() {
        return championshipRepository.findAll();
    }

    /**
     * @description Obtiene un campeonato por ID
     * @param id Championship id
     * @return Championship
     */
    public Championship getChampionshipById(Long id) {
        return championshipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campeonato no encontrado con id: " + id));
    }

    /**
     * @description Guarda un campeonato
     * @param championship Championship
     * @return Championship guardado
     */
    public Championship saveChampionship(Championship championship) {
        return championshipRepository.save(championship);
    }

    /**
     * @description Actualiza un campeonato
     * @param id                  Championship id
     * @param championshipDetails Championship details
     * @return Championship actualizado
     */
    public Championship updateChampionship(Long id, Championship championshipDetails) {
        Championship championship = getChampionshipById(id);

        championship.setName(championshipDetails.getName());
        championship.setCategory(championshipDetails.getCategory());
        championship.setCountry(championshipDetails.getCountry());
        championship.setSeason(championshipDetails.getSeason());

        return championshipRepository.save(championship);
    }

    /**
     * @description Elimina un campeonato
     * @param id Championship id
     */
    public void deleteChampionship(Long id) {
        Championship championship = getChampionshipById(id);
        championshipRepository.delete(championship);
    }
}
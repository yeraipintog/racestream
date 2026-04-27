/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 07-04-2026
 * @modified 27-04-2026
 * @description Repositorio de carreras con consultas basicas y comprobacion de duplicados
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.entity.Race;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RaceRepository extends JpaRepository<Race, Long> {
    List<Race> findByChampionshipId(Long championshipId);

    List<Race> findByCircuitId(Long circuitId);

    boolean existsByNameIgnoreCaseAndSeasonAndChampionshipIdAndCircuitId(
            String name,
            Integer season,
            Long championshipId,
            Long circuitId);
}

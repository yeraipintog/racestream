/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 09-03-2026
 * @modified 27-04-2026
 * @description Repositorio de Championship
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.entity.Championship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChampionshipRepository extends JpaRepository<Championship, Long> {
    Optional<Championship> findByNameIgnoreCaseAndSeason(String name, Integer season);
}

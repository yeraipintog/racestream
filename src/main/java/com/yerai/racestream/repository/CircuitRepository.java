/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 07-04-2026
 * @description Repositorio de Circuit
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.entity.Circuit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CircuitRepository extends JpaRepository<Circuit, Long> {
    Optional<Circuit> findByNameIgnoreCase(String name);
}
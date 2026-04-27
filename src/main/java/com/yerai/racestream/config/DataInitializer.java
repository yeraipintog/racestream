/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 07-04-2026
 * @modified 27-04-2026
 * @description Inicializador de datos minimos de Formula 1 para desarrollo local
 */
package com.yerai.racestream.config;

import com.yerai.racestream.entity.Championship;
import com.yerai.racestream.entity.Circuit;
import com.yerai.racestream.entity.Race;
import com.yerai.racestream.repository.ChampionshipRepository;
import com.yerai.racestream.repository.CircuitRepository;
import com.yerai.racestream.repository.RaceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DataInitializer implements CommandLineRunner {

    private final ChampionshipRepository championshipRepository;
    private final CircuitRepository circuitRepository;
    private final RaceRepository raceRepository;

    public DataInitializer(ChampionshipRepository championshipRepository,
            CircuitRepository circuitRepository,
            RaceRepository raceRepository) {
        this.championshipRepository = championshipRepository;
        this.circuitRepository = circuitRepository;
        this.raceRepository = raceRepository;
    }

    @Override
    public void run(String... args) {
        Championship formulaOne = championshipRepository.findByNameIgnoreCaseAndSeason("Formula 1", 2026)
                .orElseGet(() -> championshipRepository.save(
                        new Championship("Formula 1", "Single-Seater", "Internacional", 2026)));

        Circuit barcelona = circuitRepository.findByNameIgnoreCase("Circuit de Barcelona-Catalunya")
                .orElseGet(() -> circuitRepository.save(
                        new Circuit("Circuit de Barcelona-Catalunya", "Montmelo, Espana", 4.657)));

        Circuit silverstone = circuitRepository.findByNameIgnoreCase("Silverstone Circuit")
                .orElseGet(() -> circuitRepository.save(
                        new Circuit("Silverstone Circuit", "Silverstone, Reino Unido", 5.891)));

        createRaceIfNotExists("Gran Premio de Espana", LocalDate.of(2026, 6, 14), 9, 2026, formulaOne, barcelona);
        createRaceIfNotExists("British Grand Prix", LocalDate.of(2026, 7, 5), 11, 2026, formulaOne, silverstone);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 07-04-2026
     * @modified 27-04-2026
     * @description Crea una carrera solo si no existe para la temporada, campeonato y circuito indicados
     * @param name Nombre de la carrera
     * @param raceDate Fecha de carrera
     * @param roundNumber Numero de ronda
     * @param season Temporada
     * @param championship Campeonato asociado
     * @param circuit Circuito asociado
     */
    private void createRaceIfNotExists(String name, LocalDate raceDate, Integer roundNumber, Integer season,
            Championship championship, Circuit circuit) {
        boolean exists = raceRepository.existsByNameIgnoreCaseAndSeasonAndChampionshipIdAndCircuitId(
                name,
                season,
                championship.getId(),
                circuit.getId());

        if (!exists) {
            raceRepository.save(new Race(name, raceDate, roundNumber, season, championship, circuit));
        }
    }
}

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 07-04-2026
 * @description Inicializador de datos
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
                Championship f1 = championshipRepository.findByNameIgnoreCaseAndSeason("Formula 1", 2026)
                                .orElseGet(() -> championshipRepository.save(
                                                new Championship("Formula 1", "Single-Seater", "Internacional", 2026)));

                Championship motogp = championshipRepository.findByNameIgnoreCaseAndSeason("MotoGP", 2026)
                                .orElseGet(() -> championshipRepository.save(
                                                new Championship("MotoGP", "Motorcycle", "Internacional", 2026)));

                Championship wec = championshipRepository.findByNameIgnoreCaseAndSeason("WEC", 2026)
                                .orElseGet(() -> championshipRepository.save(
                                                new Championship("WEC", "Endurance", "Internacional", 2026)));

                Circuit barcelona = circuitRepository.findByNameIgnoreCase("Circuit de Barcelona-Catalunya")
                                .orElseGet(() -> circuitRepository.save(
                                                new Circuit("Circuit de Barcelona-Catalunya", "Montmeló, España",
                                                                4.657)));

                Circuit silverstone = circuitRepository.findByNameIgnoreCase("Silverstone Circuit")
                                .orElseGet(() -> circuitRepository.save(
                                                new Circuit("Silverstone Circuit", "Silverstone, Reino Unido", 5.891)));

                Circuit leMans = circuitRepository.findByNameIgnoreCase("Circuit de la Sarthe")
                                .orElseGet(() -> circuitRepository.save(
                                                new Circuit("Circuit de la Sarthe", "Le Mans, Francia", 13.626)));

                createRaceIfNotExists("Gran Premio de España", LocalDate.of(2026, 6, 14), 9, 2026, f1, barcelona);
                createRaceIfNotExists("Gran Premi de Catalunya", LocalDate.of(2026, 5, 24), 7, 2026, motogp, barcelona);
                createRaceIfNotExists("British Grand Prix", LocalDate.of(2026, 7, 5), 11, 2026, f1, silverstone);
                createRaceIfNotExists("6 Horas de Silverstone", LocalDate.of(2026, 8, 16), 5, 2026, wec, silverstone);
                createRaceIfNotExists("24 Horas de Le Mans", LocalDate.of(2026, 6, 13), 4, 2026, wec, leMans);
        }

        private void createRaceIfNotExists(String name, LocalDate raceDate, Integer roundNumber, Integer season,
                        Championship championship, Circuit circuit) {
                boolean exists = raceRepository.findAll().stream()
                                .anyMatch(race -> race.getName().equalsIgnoreCase(name)
                                                && race.getSeason().equals(season)
                                                && race.getChampionship().getId().equals(championship.getId())
                                                && race.getCircuit().getId().equals(circuit.getId()));

                if (!exists) {
                        raceRepository.save(new Race(name, raceDate, roundNumber, season, championship, circuit));
                }
        }
}

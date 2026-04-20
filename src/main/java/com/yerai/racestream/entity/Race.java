/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 07-04-2026
 * @description Entidad Race
 */
package com.yerai.racestream.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Entity
@Table(name = "races")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Race {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre de la carrera es obligatorio")
    @Column(nullable = false)
    private String name;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate raceDate;

    @NotNull(message = "La ronda es obligatoria")
    @Min(value = 1, message = "La ronda debe ser mayor que 0")
    private Integer roundNumber;

    @NotNull(message = "La temporada es obligatoria")
    @Min(value = 1900, message = "La temporada no puede ser inferior a 1900")
    private Integer season;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "championship_id", nullable = false)
    private Championship championship;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "circuit_id", nullable = false)
    private Circuit circuit;

    public Race() {
    }

    public Race(String name, LocalDate raceDate, Integer roundNumber, Integer season,
            Championship championship, Circuit circuit) {
        this.name = name;
        this.raceDate = raceDate;
        this.roundNumber = roundNumber;
        this.season = season;
        this.championship = championship;
        this.circuit = circuit;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getRaceDate() {
        return raceDate;
    }

    public Integer getRoundNumber() {
        return roundNumber;
    }

    public Integer getSeason() {
        return season;
    }

    public Championship getChampionship() {
        return championship;
    }

    public Circuit getCircuit() {
        return circuit;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRaceDate(LocalDate raceDate) {
        this.raceDate = raceDate;
    }

    public void setRoundNumber(Integer roundNumber) {
        this.roundNumber = roundNumber;
    }

    public void setSeason(Integer season) {
        this.season = season;
    }

    public void setChampionship(Championship championship) {
        this.championship = championship;
    }

    public void setCircuit(Circuit circuit) {
        this.circuit = circuit;
    }
}

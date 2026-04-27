/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 09-03-2026
 * @modified 27-04-2026
 * @description Entidad Championship
 */
package com.yerai.racestream.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Entity
@Table(name = "championships")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Championship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    @Column(nullable = false, length = 100)
    private String name;

    @Size(max = 50, message = "La categoría no puede superar los 50 caracteres")
    @Column(length = 50)
    private String category;

    @Size(max = 50, message = "El país no puede superar los 50 caracteres")
    @Column(length = 50)
    private String country;

    @Min(value = 1950, message = "La temporada no puede ser inferior a 1950")
    @Max(value = 2027, message = "La temporada no puede ser superior a 2027")
    private Integer season;

    public Championship() {
    }

    public Championship(String name, String category, String country, Integer season) {
        this.name = name;
        this.category = category;
        this.country = country;
        this.season = season;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Integer getSeason() {
        return season;
    }

    public void setSeason(Integer season) {
        this.season = season;
    }
}

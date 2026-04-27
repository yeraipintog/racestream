/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 07-04-2026
 * @description Entidad Circuit
 */
package com.yerai.racestream.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "circuits")
public class Circuit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    @Column(nullable = false, length = 100)
    private String name;

    @Size(max = 100, message = "La localización no puede superar los 100 caracteres")
    private String location;

    @DecimalMin(value = "0.1", message = "La longitud debe ser mayor que 0")
    private Double lengthKm;

    public Circuit() {
    }

    public Circuit(String name, String location, Double lengthKm) {
        this.name = name;
        this.location = location;
        this.lengthKm = lengthKm;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public Double getLengthKm() {
        return lengthKm;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setLengthKm(Double lengthKm) {
        this.lengthKm = lengthKm;
    }
}

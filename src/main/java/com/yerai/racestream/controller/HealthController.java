/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 01-06-2026
 * @modified 01-06-2026
 * @description Controlador ligero para comprobar que RaceStream está arrancado correctamente en Railway.
 */
package com.yerai.racestream.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 01-06-2026
 * @modified 01-06-2026
 * @description Expone un endpoint público de salud para Railway.
 */
@RestController
public class HealthController {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 01-06-2026
     * @modified 01-06-2026
     * @description Devuelve el estado básico de la aplicación para el healthcheck de Railway.
     * @return Respuesta HTTP 200 si la aplicación está viva.
     */
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "application", "RaceStream"
        ));
    }
}
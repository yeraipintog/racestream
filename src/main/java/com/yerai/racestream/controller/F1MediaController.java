/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 30-04-2026
 * @description Controlador REST para recursos visuales de pilotos y escuderias
 */
package com.yerai.racestream.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerai.racestream.service.F1MediaService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/f1/media")
@CrossOrigin(origins = "*")
public class F1MediaController {

    private final F1MediaService f1MediaService;

    public F1MediaController(F1MediaService f1MediaService) {
        this.f1MediaService = f1MediaService;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Devuelve imagen de piloto desde OpenF1
     * @param number Numero permanente del piloto
     * @return Datos visuales del piloto
     */
    @GetMapping("/driver")
    public JsonNode getDriverImage(@RequestParam Integer number) {
        return f1MediaService.getDriverImage(number);
    }
}

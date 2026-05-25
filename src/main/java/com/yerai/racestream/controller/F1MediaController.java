/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 30-04-2026
 * @description Controlador REST para recursos visuales de pilotos y escuderías
 */
package com.yerai.racestream.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerai.racestream.service.F1MediaService;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

@RestController
@RequestMapping("/api/f1/media")
@CrossOrigin(origins = "*")
public class F1MediaController {

    private final F1MediaService f1MediaService;
    private final RestTemplate restTemplate;

    public F1MediaController(F1MediaService f1MediaService, RestTemplateBuilder restTemplateBuilder) {
        this.f1MediaService = f1MediaService;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(4))
                .readTimeout(Duration.ofSeconds(12))
                .build();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Devuelve imagen de piloto desde OpenF1
     * @param number Número permanente del piloto
     * @return Datos visuales del piloto
     */
    @GetMapping("/driver")
    public JsonNode getDriverImage(@RequestParam Integer number) {
        return f1MediaService.getDriverImage(number);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 24-05-2026
     * @description Sirve radios de equipo desde URL pública de OpenF1/F1 sin
     *              exponer credenciales y evitando salir de RaceStream
     * @param url URL pública del audio de team radio
     * @return Audio listo para el reproductor HTML
     */
    @GetMapping("/team-radio")
    public ResponseEntity<byte[]> getTeamRadio(@RequestParam String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
        if (!isAllowedAudioUrl(uri)) {
            return ResponseEntity.badRequest().build();
        }
        byte[] audio = restTemplate.getForObject(uri, byte[].class);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(audio == null ? new byte[0] : audio);
    }

    private boolean isAllowedAudioUrl(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        return "https".equalsIgnoreCase(scheme)
                && !host.isBlank()
                && !host.equals("localhost")
                && !host.startsWith("127.")
                && !host.startsWith("10.")
                && !host.startsWith("192.168.")
                && !host.endsWith(".local");
    }
}

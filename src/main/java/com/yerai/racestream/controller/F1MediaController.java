/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.1
 * @created 30-04-2026
 * @modified 26-05-2026
 * @description Controlador REST para recursos visuales de pilotos y escuderías
 */
package com.yerai.racestream.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerai.racestream.service.F1MediaService;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/f1/media")
public class F1MediaController {

    private static final int MAX_TEAM_RADIO_BYTES = 6 * 1024 * 1024;
    private static final List<String> ALLOWED_AUDIO_HOSTS = List.of(
            "livetiming.formula1.com",
            "media.formula1.com",
            "static.formula1.com",
            "static-files.formula1.com",
            "www.formula1.com",
            "api.openf1.org");

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
     * @version 1.0.1
     * @created 24-05-2026
     * @modified 26-05-2026
     * @description Sirve radios de equipo desde URL pública de OpenF1/F1 sin
     *              exponer credenciales, con origen permitido y límite de tamaño
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
        try {
            byte[] audio = restTemplate.execute(uri, HttpMethod.GET, null, response -> readLimitedAudio(response.getBody()));
            if (audio == null || audio.length == 0) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .body(audio);
        } catch (TeamRadioTooLargeException ex) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        } catch (RestClientException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 24-05-2026
     * @modified 26-05-2026
     * @description Valida que el audio proceda de hosts oficiales permitidos
     * @param uri URL recibida desde OpenF1
     * @return true si RaceStream puede proxyear el audio
     */
    private boolean isAllowedAudioUrl(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        return "https".equalsIgnoreCase(scheme)
                && ALLOWED_AUDIO_HOSTS.contains(host)
                && (path.endsWith(".mp3") || path.endsWith(".m4a") || path.contains("teamradio"));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 26-05-2026
     * @modified 26-05-2026
     * @description Lee el audio con límite de bytes para evitar respuestas grandes
     * @param input Flujo remoto
     * @return Audio validado
     * @throws IOException Si falla la lectura del flujo
     */
    private byte[] readLimitedAudio(InputStream input) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_TEAM_RADIO_BYTES) {
                throw new TeamRadioTooLargeException();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static class TeamRadioTooLargeException extends RuntimeException {
    }
}

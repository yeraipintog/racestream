/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 21-04-2026
 * @modified 28-04-2026
 * @description Configuracion de RestTemplate con timeouts para APIs externas
 */
package com.yerai.racestream.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 21-04-2026
     * @modified 28-04-2026
     * @description Crea un cliente HTTP con limites cortos para no bloquear la web
     *              si una API externa falla
     * @param builder Constructor de RestTemplate
     * @return Cliente HTTP
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(4))
                .readTimeout(Duration.ofSeconds(6))
                .build();
    }
}

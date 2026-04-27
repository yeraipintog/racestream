/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 21-04-2026
 * @description Configuración de RestTemplate
 */
package com.yerai.racestream.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
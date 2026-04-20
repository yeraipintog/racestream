/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0
 * @created 17-04-2026
 * @description Servicio para obtener datos de OpenF1
 * @see https://openf1.org
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OpenF1Service {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openf1.api.base-url}")
    private String openF1BaseUrl;

    public JsonNode getMeetings(Integer year) {
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/meetings")
                .queryParam("year", year)
                .toUriString();

        return restTemplate.getForObject(url, JsonNode.class);
    }

    public JsonNode getSessions(Integer meetingKey) {
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/sessions")
                .queryParam("meeting_key", meetingKey)
                .toUriString();

        return restTemplate.getForObject(url, JsonNode.class);
    }

    public JsonNode getDrivers(Integer sessionKey) {
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/drivers")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return restTemplate.getForObject(url, JsonNode.class);
    }

    public JsonNode getSessionResults(Integer sessionKey) {
        String url = UriComponentsBuilder
                .fromHttpUrl(openF1BaseUrl + "/session_result")
                .queryParam("session_key", sessionKey)
                .toUriString();

        return restTemplate.getForObject(url, JsonNode.class);
    }
}
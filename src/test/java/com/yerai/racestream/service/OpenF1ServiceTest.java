/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 12-05-2026
 * @description Tests de cache defensiva de OpenF1 sin llamadas reales a internet
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenF1ServiceTest {

    private static final String BASE_URL = "https://api.test/v1";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OpenF1Service service;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Prepara OpenF1 mockeado para cada prueba
     */
    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new OpenF1Service(restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "openF1BaseUrl", BASE_URL);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que una respuesta vacia posterior no sustituye una cache valida
     */
    @Test
    void emptyResponseKeepsLastValidCache() {
        String url = BASE_URL + "/meetings?year=2026";
        server.expect(requestTo(url)).andRespond(withSuccess("[{\"meeting_key\":1}]", MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.times(3), requestTo(url)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        ArrayNode first = (ArrayNode) service.getMeetings(2026);
        ArrayNode second = (ArrayNode) service.getMeetings(2026);

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).path("meeting_key").asInt()).isEqualTo(1);
        server.verify();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que un fallo temporal devuelve el ultimo dato valido cacheado
     */
    @Test
    void externalFailureReturnsLastValidCache() {
        String url = BASE_URL + "/meetings?year=2025";
        server.expect(requestTo(url)).andRespond(withSuccess("[{\"meeting_key\":2}]", MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.times(3), requestTo(url)).andRespond(withServerError());

        ArrayNode first = (ArrayNode) service.getMeetings(2025);
        ArrayNode second = (ArrayNode) service.getMeetings(2025);

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).path("meeting_key").asInt()).isEqualTo(2);
        server.verify();
    }
}

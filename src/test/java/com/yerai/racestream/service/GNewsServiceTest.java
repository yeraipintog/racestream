/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 31-05-2026
 * @modified 31-05-2026
 * @description Tests de GNews con contenido completo sin llamadas externas
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GNewsServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 31-05-2026
     * @modified 31-05-2026
     * @description Verifica que GNews se consulta sobre contenido completo, filtra falsos positivos y conserva el texto recibido
     * @throws Exception Error de parseo o mock HTTP
     */
    @Test
    void formulaOneNewsRequestsContentSearchAndKeepsFullContent() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        GNewsService service = new GNewsService(restTemplate, objectMapper);
        ReflectionTestUtils.setField(service, "gNewsBaseUrl", "https://gnews.io/api/v4");
        ReflectionTestUtils.setField(service, "gNewsApiKey", "test-key");

        server.expect(request -> {
                    String query = URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8);
                    assertThat(request.getURI().getPath()).isEqualTo("/api/v4/search");
                    assertThat(query).contains("q=Formula 1");
                    assertThat(query).contains("in=title,description,content");
                    assertThat(query).contains("nullable=image");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"articles":[{"title":"Casi entro en hipotermia","description":"Entrevista con una deportista de deportes extremos","content":"Compite en E1 Series, la Formula 1 del agua.","url":"https://example.com/e1","image":null,"publishedAt":"2026-05-31T06:00:00Z","source":{"name":"General Example","url":"https://example.com"}},{"title":"Lo que no se vio del Gran Premio de Italia","description":"Aprilia gana en Mugello y Kimi Antonelli visita MotoGP","content":"Crónica de MotoGP con una mención secundaria a Formula 1.","url":"https://example.com/moto","image":null,"publishedAt":"2026-05-31T07:00:00Z","source":{"name":"Moto Example","url":"https://example.com"}},{"title":"Formula 1 confirma novedades","description":"Resumen F1","content":"Texto completo de Formula 1 con varias frases para la vista ampliada.","url":"https://example.com/f1","image":null,"publishedAt":"2026-05-31T08:00:00Z","source":{"name":"Example F1","url":"https://example.com"}}]}
                        """, MediaType.APPLICATION_JSON));

        ArrayNode news = service.getFormulaOneNews(1);

        assertThat(news).hasSize(1);
        assertThat(news.get(0).path("url").asText()).isEqualTo("https://example.com/f1");
        assertThat(news.get(0).path("content").asText()).contains("Texto completo de Formula 1");
        server.verify();
    }
}

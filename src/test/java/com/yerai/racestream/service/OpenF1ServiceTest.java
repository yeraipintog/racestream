/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 12-05-2026
 * @modified 23-05-2026
 * @description Tests de autenticación, caché defensiva y reintentos de OpenF1
 *              sin llamadas reales a internet
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenF1ServiceTest {

    private static final String BASE_URL = "https://api.test/v1";
    private static final String TOKEN_URL = "https://api.openf1.org/token";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OpenF1Service service;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 12-05-2026
     * @modified 23-05-2026
     * @description Prepara OpenF1 mockeado para cada prueba
     */
    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = service("", "", "");
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.invokeMethod(service, "shutdownTokenRefresher");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @description Verifica que una respuesta cacheada en TTL evita nuevas llamadas
     */
    @Test
    void freshCacheAvoidsSecondExternalCall() {
        String url = BASE_URL + "/meetings?year=2026";
        server.expect(requestTo(url)).andRespond(withSuccess("[{\"meeting_key\":1}]", MediaType.APPLICATION_JSON));

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
     * @version 1.0.1
     * @created 12-05-2026
     * @modified 23-05-2026
     * @description Verifica que un fallo temporal devuelve el último dato válido cacheado
     * @throws InterruptedException si la espera de TTL se interrumpe
     */
    @Test
    void externalFailureReturnsLastValidCache() throws InterruptedException {
        String url = BASE_URL + "/laps?session_key=latest";
        server.expect(requestTo(url)).andRespond(withSuccess("[{\"driver_number\":1,\"lap_number\":10}]", MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.times(3), requestTo(url)).andRespond(withServerError());

        ArrayNode first = (ArrayNode) service.getLaps("latest");
        Thread.sleep(4300L);
        ArrayNode second = (ArrayNode) service.getLaps("latest");

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).path("lap_number").asInt()).isEqualTo(10);
        server.verify();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @description Verifica que un 429 mantiene el último dato válido y no
     *              sustituye la respuesta por un array vacío temporal
     * @throws InterruptedException si la espera de TTL se interrumpe
     */
    @Test
    void tooManyRequestsReturnsLastValidCache() throws InterruptedException {
        String url = BASE_URL + "/position?session_key=latest";
        server.expect(requestTo(url)).andRespond(withSuccess("[{\"driver_number\":1,\"position\":1}]", MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.times(3), requestTo(url)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        ArrayNode first = (ArrayNode) service.getPosition("latest");
        Thread.sleep(4300L);
        ArrayNode second = (ArrayNode) service.getPosition("latest");

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).path("position").asInt()).isEqualTo(1);
        server.verify();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 22-05-2026
     * @description Verifica que OpenF1 restringido en directo no reintenta ni bloquea la página
     */
    @Test
    void restrictedLiveAccessReturnsEmptyWithoutRetrying() {
        String url = BASE_URL + "/weather?session_key=latest";
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .body("{\"detail\":\"Live F1 session in progress\"}")
                .contentType(MediaType.APPLICATION_JSON));

        ArrayNode result = (ArrayNode) service.getWeather("latest");

        assertThat(result).isEmpty();
        server.verify();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @description Verifica lectura de usuario y contraseña inyectados desde properties
     *              y generación automática de token
     */
    @Test
    void propertiesCredentialsGenerateBearerToken() {
        service = service("property-user", "property-password", "");
        expectTokenRequest("property-user", "property-password", "generated-token", 3600);
        String url = BASE_URL + "/weather?session_key=latest";
        server.expect(requestTo(url))
                .andExpect(header("Authorization", "Bearer generated-token"))
                .andRespond(withSuccess("[{\"air_temperature\":22}]", MediaType.APPLICATION_JSON));

        ArrayNode result = (ArrayNode) service.getWeather("latest");

        assertThat(result).hasSize(1);
        server.verify();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @description Verifica renovación del token cuando queda menos de un minuto de vida útil
     */
    @Test
    void expiredTokenIsRenewedBeforeNextRequest() {
        service = service("property-user", "property-password", "");
        expectTokenRequest("property-user", "property-password", "short-token", 1);
        String weatherUrl = BASE_URL + "/weather?session_key=latest";
        server.expect(requestTo(weatherUrl))
                .andExpect(header("Authorization", "Bearer short-token"))
                .andRespond(withSuccess("[{\"air_temperature\":20}]", MediaType.APPLICATION_JSON));
        expectTokenRequest("property-user", "property-password", "renewed-token", 3600);
        String raceControlUrl = BASE_URL + "/race_control?session_key=latest";
        server.expect(requestTo(raceControlUrl))
                .andExpect(header("Authorization", "Bearer renewed-token"))
                .andRespond(withSuccess("[{\"message\":\"ok\"}]", MediaType.APPLICATION_JSON));

        assertThat((ArrayNode) service.getWeather("latest")).hasSize(1);
        assertThat((ArrayNode) service.getRaceControl("latest")).hasSize(1);
        server.verify();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @description Verifica reintento único con token renovado tras un 401
     */
    @Test
    void unauthorizedResponseRenewsTokenAndRetriesOnce() {
        service = service("property-user", "property-password", "");
        expectTokenRequest("property-user", "property-password", "expired-token", 3600);
        String url = BASE_URL + "/position?session_key=latest";
        server.expect(requestTo(url))
                .andExpect(header("Authorization", "Bearer expired-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectTokenRequest("property-user", "property-password", "fresh-token", 3600);
        server.expect(requestTo(url))
                .andExpect(header("Authorization", "Bearer fresh-token"))
                .andRespond(withSuccess("[{\"driver_number\":1,\"position\":1}]", MediaType.APPLICATION_JSON));

        ArrayNode result = (ArrayNode) service.getPosition("latest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).path("position").asInt()).isEqualTo(1);
        server.verify();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @description Verifica que el access-token de properties se envía sin exponer credenciales
     */
    @Test
    void propertyAccessTokenIsUsedWithoutTokenRequest() {
        service = service("", "", "static-token");
        String url = BASE_URL + "/position?session_key=latest";
        server.expect(requestTo(url))
                .andExpect(header("Authorization", "Bearer static-token"))
                .andRespond(withSuccess("[{\"driver_number\":1,\"position\":1}]", MediaType.APPLICATION_JSON));

        ArrayNode result = (ArrayNode) service.getPosition("latest");

        assertThat(result).hasSize(1);
        server.verify();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 23-05-2026
     * @description Verifica que sin credenciales ni access-token no se inventa
     *              autenticación ni se solicita token
     */
    @Test
    void missingCredentialsDoNotRequestTokenOrSendAuthorization() {
        service = service("", "", "");
        String url = BASE_URL + "/weather?session_key=latest";
        server.expect(requestTo(url))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ArrayNode result = (ArrayNode) service.getWeather("latest");

        assertThat(result).isEmpty();
        server.verify();
    }

    private OpenF1Service service(String username, String password, String accessToken) {
        return new OpenF1Service(restTemplate, new ObjectMapper(), BASE_URL, username, password, accessToken);
    }

    private void expectTokenRequest(String username, String password, String token, long expiresIn) {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("username=" + username)))
                .andExpect(content().string(containsString("password=" + password)))
                .andRespond(withSuccess("{\"access_token\":\"" + token + "\",\"expires_in\":" + expiresIn + "}",
                        MediaType.APPLICATION_JSON));
    }
}

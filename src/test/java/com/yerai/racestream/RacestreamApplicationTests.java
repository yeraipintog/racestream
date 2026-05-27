package com.yerai.racestream;

import com.yerai.racestream.model.BlockedEmail;
import com.yerai.racestream.repository.BlockedEmailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RacestreamApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RacestreamApplicationTests {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private BlockedEmailRepository blockedEmailRepository;

	/**
	 * @author Yerai Pinto
	 * @since 1.0
	 * @version 1.0.0
	 * @created 06-05-2026
	 * @description Verifica que el contexto Spring arranca correctamente
	 */
	@Test
	void contextLoads() {
	}

	/**
	 * @author Yerai Pinto
	 * @since 1.0
	 * @version 1.0.1
	 * @created 06-05-2026
	 * @modified 26-05-2026
	 * @description Verifica el acceso especial admin/admin con rol ADMIN
	 */
	@Test
	void adminAliasLoginReturnsAdminRole() {
		ResponseEntity<Map> response = restTemplate.postForEntity(
				"/api/auth/login",
				csrfJson(Map.of("email", "admin", "password", "admin")),
				Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("role", "ADMIN");
		assertThat(response.getBody()).containsEntry("cookieConsentStatus", "ACCEPTED");
	}

	/**
	 * @author Yerai Pinto
	 * @since 1.0
	 * @version 1.0.2
	 * @created 06-05-2026
	 * @modified 26-05-2026
	 * @description Verifica que el nombre de usuario visible también permite
	 *              iniciar sesión
	 */
	@Test
	void registeredUserCanLoginWithUsername() {
		String suffix = uniqueSuffix();
		String username = "PilotoVisible" + suffix;
		String email = "piloto.visible." + suffix + "@racestream.local";

		ResponseEntity<Map> registerResponse = restTemplate.postForEntity(
				"/api/auth/register",
				csrfJson(Map.of(
						"name", username,
						"email", email,
						"password", "RaceStream1!",
						"confirmPassword", "RaceStream1!",
						"acceptPolicies", true)),
				Map.class);

		assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

		ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
				"/api/auth/login",
				csrfJson(Map.of("email", username, "password", "RaceStream1!")),
				Map.class);

		assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(loginResponse.getBody()).containsEntry("email", email);
		assertThat(loginResponse.getBody()).containsEntry("cookieConsentStatus", "UNDECIDED");
	}

	/**
	 * @author Yerai Pinto
	 * @since 1.0
	 * @version 1.0.2
	 * @created 06-05-2026
	 * @modified 26-05-2026
	 * @description Verifica que un correo bloqueado no pueda registrarse de nuevo
	 */
	@Test
	void blockedEmailCannotRegister() {
		String suffix = uniqueSuffix();
		String email = "bloqueado." + suffix + "@racestream.local";

		BlockedEmail blockedEmail = new BlockedEmail();
		blockedEmail.setEmail(email);
		blockedEmail.setReason("Test");
		blockedEmailRepository.save(blockedEmail);

		ResponseEntity<Map> response = restTemplate.postForEntity(
				"/api/auth/register",
				csrfJson(Map.of(
						"name", "UsuarioBloqueado" + suffix,
						"email", email,
						"password", "RaceStream1!",
						"confirmPassword", "RaceStream1!",
						"acceptPolicies", true)),
				Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).containsKey("error");
	}

	/**
	 * @author Yerai Pinto
	 * @since 1.0
	 * @version 1.0.0
	 * @created 12-05-2026
	 * @modified 12-05-2026
	 * @description Genera sufijos válidos para que las pruebas de registro no
	 *              dependan de datos previos
	 */
	private String uniqueSuffix() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	/**
	 * @author Yerai Pinto
	 * @since 1.0
	 * @version 1.0.0
	 * @created 26-05-2026
	 * @modified 26-05-2026
	 * @description Construye una petición JSON con el token CSRF que recibe el
	 *              navegador al abrir una página pública
	 * @param body Cuerpo JSON
	 * @return Petición HTTP con cookie y cabecera CSRF
	 */
	private HttpEntity<?> csrfJson(Map<String, ?> body) {
		ResponseEntity<String> page = restTemplate.getForEntity("/login.html", String.class);
		String csrfCookie = page.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
				.filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No se ha emitido XSRF-TOKEN"));
		String csrfCookiePair = csrfCookie.split(";", 2)[0];
		String csrfToken = URLDecoder.decode(csrfCookiePair.substring("XSRF-TOKEN=".length()), StandardCharsets.UTF_8);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add(HttpHeaders.COOKIE, csrfCookiePair);
		headers.add("X-XSRF-TOKEN", csrfToken);
		return new HttpEntity<>(body, headers);
	}
}

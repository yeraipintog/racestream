/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.1
 * @created 30-04-2026
 * @modified 03-05-2026
 * @description Servicio para obtener noticias de Fórmula 1 en español desde GNews con búsqueda robusta y filtro temático
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
public class GNewsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GNewsService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final Duration EMPTY_CACHE_TTL = Duration.ofMinutes(5);
    private static final Pattern FORMULA_ONE_PATTERN = Pattern.compile(
            "f[oó]rmula\\s*1|formula\\s*one|\\bf1\\b|grand prix|gran premio|fia|verstappen|hamilton|alonso|sainz|norris|leclerc|piastri|russell|antonelli|bearman|bortoleto|lindblad|lawson|tsunoda|ocon|gasly|albon|hulkenberg|hülkenberg|stroll|colapinto|ferrari|mclaren|mercedes|red bull|racing bulls|aston martin|williams|alpine|haas|sauber|cadillac|audi",
            Pattern.CASE_INSENSITIVE
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicReference<ArrayNode> cachedNews = new AtomicReference<>();
    private Instant cachedAt = Instant.EPOCH;

    @Value("${gnews.api.base-url:https://gnews.io/api/v4}")
    private String gNewsBaseUrl;

    @Value("${gnews.api.key:}")
    private String gNewsApiKey;

    public GNewsService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 30-04-2026
     * @modified 30-04-2026
     * @description Busca noticias actuales de Fórmula 1 en español con una sola petición cacheada para proteger la cuota diaria
     * @param limit Numero maximo de noticias
     * @return Noticias de GNews
     */
    public ArrayNode getFormulaOneNews(Integer limit) {
        if (gNewsApiKey == null || gNewsApiKey.isBlank()) {
            return objectMapper.createArrayNode();
        }

        int max = Math.max(1, Math.min(limit == null ? 6 : limit, 10));
        ArrayNode cached = cachedNews.get();
        if (cached != null && cachedAt.plus(cached.size() == 0 ? EMPTY_CACHE_TTL : CACHE_TTL).isAfter(Instant.now())) {
            return copyLimited(cached, max);
        }

        ArrayNode freshNews = copyLimited(fetchArticles("Formula 1", max), max);
        if (freshNews.size() == 0) {
            freshNews = copyLimited(fetchArticles("Fórmula 1", max), max);
        }
        if (freshNews.size() == 0) {
            freshNews = copyLimited(fetchArticles("F1", max), max);
        }
        cachedNews.set(freshNews.deepCopy());
        cachedAt = Instant.now();
        return freshNews;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Ejecuta una búsqueda GNews sin propagar errores a la vista de Inicio
     * @param query Consulta simple comprobada contra GNews
     * @param max Maximo solicitado a GNews
     * @return Articulos recibidos
     */
    private JsonNode fetchArticles(String query, int max) {
        URI url = UriComponentsBuilder
                .fromHttpUrl(normalizedBaseUrl())
                .pathSegment("search")
                .queryParam("q", query)
                .queryParam("lang", "es")
                .queryParam("max", max)
                .queryParam("sortby", "publishedAt")
                .queryParam("apikey", gNewsApiKey.trim())
                .build()
                .encode()
                .toUri();
        try {
            ResponseEntity<String> entity = restTemplate.getForEntity(url, String.class);
            JsonNode response = objectMapper.readTree(entity.getBody());
            JsonNode articles = response == null ? null : response.path("articles");
            if (articles == null || !articles.isArray()) {
                LOGGER.warn("GNews no devolvio el array articles para la consulta {}", query);
            }
            return articles != null && articles.isArray() ? articles : objectMapper.createArrayNode();
        } catch (HttpStatusCodeException ex) {
            LOGGER.warn("GNews respondio con estado {} para la consulta {}: {}", ex.getStatusCode(), query, ex.getResponseBodyAsString());
            return objectMapper.createArrayNode();
        } catch (IOException ex) {
            LOGGER.warn("No se pudo interpretar la respuesta JSON de GNews para la consulta {}", query, ex);
            return objectMapper.createArrayNode();
        } catch (RestClientException ex) {
            LOGGER.warn("No se pudo conectar con GNews para la consulta {}", query, ex);
            return objectMapper.createArrayNode();
        }
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 03-05-2026
     * @description Copia artículos válidos de Fórmula 1 hasta el límite pedido
     * @param articles Articulos recibidos de GNews
     * @param max Limite final
     * @return Articulos filtrados
     */
    private ArrayNode copyLimited(JsonNode articles, int max) {
        ArrayNode target = objectMapper.createArrayNode();
        for (JsonNode article : articles) {
            String title = text(article, "title");
            String url = text(article, "url");
            if (target.size() >= max || title.isBlank() || url.isBlank() || !isFormulaOneArticle(article)) {
                continue;
            }
            target.add(article);
        }
        return target;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 03-05-2026
     * @description Comprueba que la noticia contenga señales claras de Fórmula 1
     * @param article Articulo de GNews
     * @return true si pertenece a Fórmula 1
     */
    private boolean isFormulaOneArticle(JsonNode article) {
        String source = text(article == null ? null : article.path("source"), "name");
        String searchable = String.join(" ",
                text(article, "title"),
                text(article, "description"),
                text(article, "content"),
                source
        );
        return FORMULA_ONE_PATTERN.matcher(searchable).find();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Normaliza la URL base para que pathSegment no duplique barras
     * @return URL base limpia
     */
    private String normalizedBaseUrl() {
        return gNewsBaseUrl == null || gNewsBaseUrl.isBlank()
                ? "https://gnews.io/api/v4"
                : gNewsBaseUrl.replaceAll("/+$", "");
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Lee texto seguro de un nodo JSON
     * @param node Nodo JSON
     * @param field Campo solicitado
     * @return Texto o vacío
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText().trim();
    }
}

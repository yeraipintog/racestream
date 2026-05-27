/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.2
 * @created 30-04-2026
 * @description Controlador REST de noticias externas en español para RaceStream
 */
package com.yerai.racestream.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.yerai.racestream.service.GNewsService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
@CrossOrigin(origins = "*")
public class NewsController {

    private final GNewsService gNewsService;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 30-04-2026
     * @description Constructor con servicio de noticias
     * @param gNewsService Servicio GNews
     */
    public NewsController(GNewsService gNewsService) {
        this.gNewsService = gNewsService;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 30-04-2026
     * @modified 27-05-2026
     * @description Devuelve noticias reales de Fórmula 1 en español
     * @param limit Límite de noticias
     * @return Noticias externas
     */
    @GetMapping("/f1")
    public JsonNode getFormulaOneNews(@RequestParam(defaultValue = "6") Integer limit) {
        return gNewsService.getFormulaOneNews(limit);
    }
}

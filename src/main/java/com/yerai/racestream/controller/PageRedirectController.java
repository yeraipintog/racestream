/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 18-05-2026
 * @modified 25-05-2026
 * @description Redirige accesos cortos a las páginas estáticas reales de
 *              RaceStream
 */
package com.yerai.racestream.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageRedirectController {

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @modified 18-05-2026
     * @description Evita que /login muestre cualquier formulario genérico y
     *              abre el acceso visual propio
     * @return Redirección a login.html
     */
    @GetMapping("/login")
    public String login() {
        return "redirect:/login.html";
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 18-05-2026
     * @modified 25-05-2026
     * @description Abre el panel de registro integrado en la pantalla de acceso,
     *              también desde la ruta HTML antigua
     * @return Redirección al panel de registro
     */
    @GetMapping({ "/register", "/register.html" })
    public String register() {
        return "redirect:/login.html#registro";
    }
}

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 18-05-2026
 * @modified 18-05-2026
 * @description Redirige accesos cortos a las paginas estaticas reales de RaceStream
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
     * @description Evita que /login muestre cualquier formulario generico y abre el acceso visual propio
     * @return Redireccion a login.html
     */
    @GetMapping("/login")
    public String login() {
        return "redirect:/login.html";
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 18-05-2026
     * @modified 18-05-2026
     * @description Abre el panel de registro integrado en la pantalla de acceso
     * @return Redireccion al panel de registro
     */
    @GetMapping("/register")
    public String register() {
        return "redirect:/login.html#registro";
    }
}

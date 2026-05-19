/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 18-05-2026
 * @modified 18-05-2026
 * @description Tests de seguridad para asegurar que RaceStream usa su login visual propio
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.RacestreamApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RacestreamApplication.class)
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginShortcutRedirectsToRaceStreamLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    void protectedPageRedirectsToRaceStreamLoginPage() throws Exception {
        mockMvc.perform(get("/account.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    void loginHtmlUsesRaceStreamPageInsteadOfSpringDefaultForm() throws Exception {
        mockMvc.perform(get("/login.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RaceStream - Acceso")))
                .andExpect(content().string(not(containsString("Please sign in"))));
    }
}

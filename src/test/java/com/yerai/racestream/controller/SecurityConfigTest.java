/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.1
 * @created 18-05-2026
 * @modified 26-05-2026
 * @description Tests de seguridad para asegurar login visual propio, estado
 *              Live público, Live Center privado y diagnóstico ADMIN
 */
package com.yerai.racestream.controller;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.yerai.racestream.RacestreamApplication;
import com.yerai.racestream.service.F1LiveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RacestreamApplication.class)
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private F1LiveService f1LiveService;

    @Test
    void loginShortcutRedirectsToRaceStreamLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    void registerHtmlRedirectsToIntegratedRegisterPanel() throws Exception {
        mockMvc.perform(get("/register.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html#registro"));
    }

    @Test
    void protectedPageRedirectsToRaceStreamLoginPage() throws Exception {
        mockMvc.perform(get("/account.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    void livePageRedirectsAnonymousUserToLoginPage() throws Exception {
        mockMvc.perform(get("/live.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    void liveStatusIsPublicForAnonymousUsers() throws Exception {
        when(f1LiveService.getLiveStatus(null))
                .thenReturn(JsonNodeFactory.instance.objectNode().put("live", false));

        mockMvc.perform(get("/api/f1/live/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.live").value(false));
    }

    @Test
    void liveDataApiRequiresAuthenticationForAnonymousUsers() throws Exception {
        mockMvc.perform(get("/api/f1/live/map"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Autenticación requerida")));
    }

    @Test
    void f1DiagnosticsRequiresAuthenticationForAnonymousUsers() throws Exception {
        mockMvc.perform(get("/api/f1/diagnostics/season?year=2026"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Autenticación requerida")));
    }

    @Test
    @WithMockUser
    void mutableUserApiRejectsMissingCsrfToken() throws Exception {
        mockMvc.perform(put("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void splitLivePagesRedirectAnonymousUserToLoginPage() throws Exception {
        mockMvc.perform(get("/live-timing.html"))
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

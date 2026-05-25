/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 13-05-2026
 * @modified 13-05-2026
 * @description Tests de validación del formulario privado de contacto
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.ContactMessage;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.ContactMessageRepository;
import com.yerai.racestream.service.ContactMailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContactControllerTest {

    private ContactController controller;
    private AppUserRepository appUserRepository;
    private ContactMessageRepository contactMessageRepository;
    private ContactMailService contactMailService;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        contactMessageRepository = mock(ContactMessageRepository.class);
        contactMailService = mock(ContactMailService.class);
        controller = new ContactController(
                appUserRepository,
                contactMessageRepository,
                contactMailService);
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("user@test.com");
        AppUser user = new AppUser();
        user.setEmail("user@test.com");
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));
        when(contactMessageRepository.findByUserAndClientRequestId(any(), any())).thenReturn(Optional.empty());
        when(contactMailService.send(any())).thenReturn(true);
        when(contactMailService.getSupportEmail()).thenReturn("contact.racestream@gmail.com");
    }

    @Test
    void rejectsEmptySubject() {
        var response = controller.send(new ContactController.ContactRequest("", "Mensaje", true, "req-1"), authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("Asunto y mensaje son obligatorios");
    }

    @Test
    void rejectsEmptyMessage() {
        var response = controller.send(new ContactController.ContactRequest("Asunto", " ", true, "req-1"), authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("Asunto y mensaje son obligatorios");
    }

    @Test
    void rejectsSubjectLongerThanTwenty() {
        var response = controller.send(new ContactController.ContactRequest("123456789012345678901", "Mensaje", true, "req-1"), authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?, ?>) response.getBody()).get("error"))
                .isEqualTo("Asunto máximo 20 caracteres y mensaje máximo 1000 caracteres");
    }

    @Test
    void rejectsMessageLongerThanOneThousand() {
        var response = controller.send(new ContactController.ContactRequest("Asunto", "a".repeat(1001), true, "req-1"), authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?, ?>) response.getBody()).get("error"))
                .isEqualTo("Asunto máximo 20 caracteres y mensaje máximo 1000 caracteres");
    }

    @Test
    void acceptsExactLimits() {
        var response = controller.send(new ContactController.ContactRequest("a".repeat(20), "b".repeat(1000), true, "req-1"), authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<?, ?>) response.getBody()).get("saved")).isEqualTo(true);
    }

    @Test
    void rejectsMissingPolicy() {
        var response = controller.send(new ContactController.ContactRequest("Asunto", "Mensaje", false, "req-1"), authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(contactMailService, never()).send(any());
    }

    @Test
    void duplicatedClientRequestDoesNotSendMailAgain() {
        when(contactMessageRepository.findByUserAndClientRequestId(any(), any()))
                .thenReturn(Optional.of(new ContactMessage()));

        var response = controller.send(new ContactController.ContactRequest("Asunto", "Mensaje", true, "req-1"), authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<?, ?>) response.getBody()).get("duplicate")).isEqualTo(true);
        verify(contactMailService, never()).send(any());
    }
}

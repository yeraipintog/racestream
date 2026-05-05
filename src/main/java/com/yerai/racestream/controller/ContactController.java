/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 05-05-2026
 * @modified 05-05-2026
 * @description API privada para formulario de contacto autenticado
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.ContactMessage;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.ContactMessageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private static final String SUPPORT_EMAIL = "yeraigonzalez100@gmail.com";

    private final AppUserRepository appUserRepository;
    private final ContactMessageRepository contactMessageRepository;

    public ContactController(AppUserRepository appUserRepository, ContactMessageRepository contactMessageRepository) {
        this.appUserRepository = appUserRepository;
        this.contactMessageRepository = contactMessageRepository;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Guarda un mensaje enviado por un usuario registrado
     * @param request Datos del mensaje
     * @param authentication Sesion actual
     * @return Estado de guardado
     */
    @PostMapping
    public ResponseEntity<?> send(@RequestBody ContactRequest request, Authentication authentication) {
        if (isBlank(request.subject()) || isBlank(request.message())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Asunto y mensaje son obligatorios"));
        }
        ContactMessage message = new ContactMessage();
        message.setUser(currentUser(authentication));
        message.setSubject(request.subject().trim());
        message.setMessage(request.message().trim());
        contactMessageRepository.save(message);
        return ResponseEntity.ok(Map.of("sent", true, "recipient", SUPPORT_EMAIL));
    }

    private AppUser currentUser(Authentication authentication) {
        return appUserRepository.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ContactRequest(String subject, String message) {}
}

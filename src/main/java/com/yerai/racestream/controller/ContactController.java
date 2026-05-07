/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.1
 * @created 05-05-2026
 * @modified 06-05-2026
 * @description API privada para formulario de contacto autenticado con persistencia y envio SMTP opcional
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.ContactMessage;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.ContactMessageRepository;
import com.yerai.racestream.service.ContactMailService;
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

    private final AppUserRepository appUserRepository;
    private final ContactMessageRepository contactMessageRepository;
    private final ContactMailService contactMailService;

    public ContactController(
            AppUserRepository appUserRepository,
            ContactMessageRepository contactMessageRepository,
            ContactMailService contactMailService) {
        this.appUserRepository = appUserRepository;
        this.contactMessageRepository = contactMessageRepository;
        this.contactMailService = contactMailService;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 05-05-2026
     * @description Guarda un mensaje enviado por un usuario registrado y lo envia por SMTP si esta activo
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
        boolean mailSent = contactMailService.send(message);
        return ResponseEntity.ok(Map.of(
                "saved", true,
                "mailSent", mailSent,
                "recipient", contactMailService.getSupportEmail()));
    }

    private AppUser currentUser(Authentication authentication) {
        return appUserRepository.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ContactRequest(String subject, String message) {}
}

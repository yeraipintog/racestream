/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.4
 * @created 05-05-2026
 * @modified 13-05-2026
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

    private static final int MAX_SUBJECT_LENGTH = 20;
    private static final int MAX_MESSAGE_LENGTH = 1000;

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
     * @version 1.0.4
     * @created 05-05-2026
     * @modified 13-05-2026
     * @description Guarda un mensaje enviado por un usuario registrado y lo envia por SMTP si esta activo
     * @param request Datos del mensaje
     * @param authentication Sesion actual
     * @return Estado de guardado
     */
    @PostMapping
    public ResponseEntity<?> send(@RequestBody ContactRequest request, Authentication authentication) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Datos de contacto obligatorios"));
        }
        if (!Boolean.TRUE.equals(request.policyAccepted())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debes aceptar la política de privacidad y las normas de contacto"));
        }
        String subject = request.subject() == null ? "" : request.subject().trim();
        String body = request.message() == null ? "" : request.message().trim();
        String clientRequestId = request.clientRequestId() == null ? "" : request.clientRequestId().trim();
        if (isBlank(subject) || isBlank(body)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Asunto y mensaje son obligatorios"));
        }
        if (subject.length() > MAX_SUBJECT_LENGTH || body.length() > MAX_MESSAGE_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of("error", "Asunto máximo 20 caracteres y mensaje máximo 1000 caracteres"));
        }
        if (isBlank(clientRequestId) || clientRequestId.length() > 80) {
            return ResponseEntity.badRequest().body(Map.of("error", "Identificador de envío no válido"));
        }
        AppUser user = currentUser(authentication);
        var existing = contactMessageRepository.findByUserAndClientRequestId(user, clientRequestId);
        if (existing.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "saved", true,
                    "duplicate", true,
                    "mailSent", false,
                    "recipient", contactMailService.getSupportEmail()));
        }
        ContactMessage message = new ContactMessage();
        message.setUser(user);
        message.setSubject(subject);
        message.setMessage(body);
        message.setClientRequestId(clientRequestId);
        contactMessageRepository.save(message);
        boolean mailSent;
        try {
            mailSent = contactMailService.send(message);
        } catch (RuntimeException ex) {
            mailSent = false;
        }
        return ResponseEntity.ok(Map.of(
                "saved", true,
                "duplicate", false,
                "mailSent", mailSent,
                "recipient", contactMailService.getSupportEmail()));
    }

    private AppUser currentUser(Authentication authentication) {
        return appUserRepository.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ContactRequest(String subject, String message, Boolean policyAccepted, String clientRequestId) {}
}

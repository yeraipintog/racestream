/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 20-05-2026
 * @modified 20-05-2026
 * @description API privada para leer y marcar avisos internos de RaceStream
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppNotification;
import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.repository.AppNotificationRepository;
import com.yerai.racestream.repository.AppUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class NotificationController {

    private final AppUserRepository appUserRepository;
    private final AppNotificationRepository appNotificationRepository;

    public NotificationController(
            AppUserRepository appUserRepository,
            AppNotificationRepository appNotificationRepository) {
        this.appUserRepository = appUserRepository;
        this.appNotificationRepository = appNotificationRepository;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 20-05-2026
     * @modified 20-05-2026
     * @description Devuelve avisos internos pendientes del usuario actual
     * @param authentication Sesión actual
     * @return Notificaciones no leídas
     */
    @GetMapping("/api/user/notifications")
    public List<Map<String, Object>> unread(Authentication authentication) {
        AppUser user = currentUser(authentication);
        return appNotificationRepository.findTop10ByUserAndReadAtIsNullOrderByCreatedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 20-05-2026
     * @modified 20-05-2026
     * @description Marca como leídos los avisos ya mostrados en la app
     * @param request Identificadores mostrados
     * @param authentication Sesión actual
     * @return Estado de guardado
     */
    @PostMapping("/api/user/notifications/read")
    public ResponseEntity<?> markRead(@RequestBody ReadRequest request, Authentication authentication) {
        AppUser user = currentUser(authentication);
        List<Long> ids = request == null || request.ids() == null ? List.of() : request.ids();
        appNotificationRepository.findByIdInAndUser(ids, user).forEach(notification -> {
            notification.setReadAt(Instant.now());
            appNotificationRepository.save(notification);
        });
        return ResponseEntity.ok(Map.of("read", ids.size()));
    }

    private AppUser currentUser(Authentication authentication) {
        return appUserRepository.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    }

    private Map<String, Object> toResponse(AppNotification notification) {
        return Map.of(
                "id", notification.getId(),
                "title", notification.getTitle(),
                "message", notification.getMessage(),
                "type", notification.getType(),
                "createdAt", notification.getCreatedAt().toString());
    }

    public record ReadRequest(List<Long> ids) {}
}

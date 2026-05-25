/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.2.1
 * @created 06-05-2026
 * @modified 22-05-2026
 * @description API privada de administración para usuarios, roles, bloqueos, contacto, foro y actividad interna
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.BlockedEmail;
import com.yerai.racestream.model.ContactMessage;
import com.yerai.racestream.model.ForumPost;
import com.yerai.racestream.model.UserRole;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.BlockedEmailRepository;
import com.yerai.racestream.repository.ContactMessageRepository;
import com.yerai.racestream.repository.ForumPostLikeRepository;
import com.yerai.racestream.repository.ForumPostRepository;
import com.yerai.racestream.repository.UserFavoriteRepository;
import com.yerai.racestream.service.UserAccountDeletionService;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AppUserRepository appUserRepository;
    private final BlockedEmailRepository blockedEmailRepository;
    private final ContactMessageRepository contactMessageRepository;
    private final ForumPostLikeRepository forumPostLikeRepository;
    private final ForumPostRepository forumPostRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final UserAccountDeletionService userAccountDeletionService;

    public AdminController(
            AppUserRepository appUserRepository,
            BlockedEmailRepository blockedEmailRepository,
            ContactMessageRepository contactMessageRepository,
            ForumPostLikeRepository forumPostLikeRepository,
            ForumPostRepository forumPostRepository,
            UserFavoriteRepository userFavoriteRepository,
            UserAccountDeletionService userAccountDeletionService) {
        this.appUserRepository = appUserRepository;
        this.blockedEmailRepository = blockedEmailRepository;
        this.contactMessageRepository = contactMessageRepository;
        this.forumPostLikeRepository = forumPostLikeRepository;
        this.forumPostRepository = forumPostRepository;
        this.userFavoriteRepository = userFavoriteRepository;
        this.userAccountDeletionService = userAccountDeletionService;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 06-05-2026
     * @modified 11-05-2026
     * @description Devuelve metricas internas solo visibles para admin
     * @return Totales de actividad privada
     */
    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return Map.of(
                "users", appUserRepository.count(),
                "contactMessages", contactMessageRepository.count(),
                "forumPosts", forumPostRepository.count(),
                "favorites", userFavoriteRepository.count());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 06-05-2026
     * @description Lista usuarios con busqueda directa por correo o nombre para revision administrativa
     * @param query Texto de busqueda opcional
     * @return Usuarios registrados
     */
    @GetMapping("/users")
    public List<Map<String, Object>> users(@RequestParam(required = false) String query) {
        List<AppUser> rows = isBlank(query)
                ? appUserRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                : appUserRepository.findTop30ByEmailContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByCreatedAtDesc(query.trim(), query.trim());
        return rows.stream()
                .map(this::toUserResponse)
                .toList();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 06-05-2026
     * @modified 06-05-2026
     * @description Lista últimos mensajes de contacto priorizando los pendientes
     * @return Mensajes recientes
     */
    @GetMapping("/contact-messages")
    public List<Map<String, Object>> contactMessages() {
        return contactMessageRepository.findTop20ByOrderByCompletedAscCreatedAtDesc().stream()
                .map(this::toMessageResponse)
                .toList();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 06-05-2026
     * @description Lista publicaciones recientes del foro para moderación
     * @return Posts recientes del foro
     */
    @GetMapping("/forum-posts")
    public List<Map<String, Object>> forumPosts() {
        return forumPostRepository.findTop30ByOrderByCreatedAtDesc().stream()
                .map(this::toForumPostResponse)
                .toList();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 06-05-2026
     * @description Lista correos bloqueados para evitar nuevos registros
     * @return Correos bloqueados
     */
    @GetMapping("/blocked-emails")
    public List<Map<String, Object>> blockedEmails() {
        return blockedEmailRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toBlockedEmailResponse)
                .toList();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 06-05-2026
     * @modified 08-05-2026
     * @description Elimina un usuario y sus datos dependientes, incluyendo hilos de foro asociados
     * @param id Identificador del usuario
     * @return Estado de borrado
     */
    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        return appUserRepository.findById(id)
                .<ResponseEntity<?>>map(user -> {
                    if (isProtectedAdmin(user)) {
                        return ResponseEntity.badRequest().body(Map.of("error", "No puedes eliminar esta cuenta de administración"));
                    }
                    userAccountDeletionService.deleteUserData(user);
                    appUserRepository.delete(user);
                    return ResponseEntity.ok(Map.of("deleted", true));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 06-05-2026
     * @description Bloquea el correo de un usuario registrado desde su ficha
     * @param id Identificador del usuario
     * @return Correo bloqueado
     */
    @PostMapping("/users/{id}/block")
    @Transactional
    public ResponseEntity<?> blockUserEmail(@PathVariable Long id) {
        return appUserRepository.findById(id)
                .<ResponseEntity<?>>map(user -> {
                    if (isProtectedAdmin(user)) {
                        return ResponseEntity.badRequest().body(Map.of("error", "No puedes bloquear esta cuenta de administración"));
                    }
                    return blockEmail(user.getEmail(), "Bloqueado");
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 06-05-2026
     * @description Desbloquea un correo previamente bloqueado
     * @param email Correo codificado en la URL
     * @return Estado de desbloqueo
     */
    @DeleteMapping("/blocked-emails/{email}")
    @Transactional
    public ResponseEntity<?> unblockEmail(@PathVariable String email) {
        blockedEmailRepository.deleteByEmailIgnoreCase(normalizeEmail(email));
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 06-05-2026
     * @description Marca un mensaje de contacto como completado
     * @param id Identificador del mensaje
     * @return Mensaje actualizado
     */
    @PatchMapping("/contact-messages/{id}/complete")
    @Transactional
    public ResponseEntity<?> completeContactMessage(@PathVariable Long id) {
        return contactMessageRepository.findById(id)
                .<ResponseEntity<?>>map(message -> {
                    message.setCompleted(true);
                    message.setCompletedAt(Instant.now());
                    return ResponseEntity.ok(toMessageResponse(contactMessageRepository.save(message)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 06-05-2026
     * @description Elimina un mensaje de contacto desde administración
     * @param id Identificador del mensaje
     * @return Estado de borrado
     */
    @DeleteMapping("/contact-messages/{id}")
    @Transactional
    public ResponseEntity<?> deleteContactMessage(@PathVariable Long id) {
        return contactMessageRepository.findById(id)
                .<ResponseEntity<?>>map(message -> {
                    contactMessageRepository.delete(message);
                    return ResponseEntity.ok(Map.of("deleted", true));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 06-05-2026
     * @modified 08-05-2026
     * @description Elimina una publicación o respuesta del foro desde administración con sus dependencias
     * @param id Identificador del post
     * @return Estado de borrado
     */
    @DeleteMapping("/forum-posts/{id}")
    @Transactional
    public ResponseEntity<?> deleteForumPost(@PathVariable Long id) {
        return forumPostRepository.findById(id)
                .<ResponseEntity<?>>map(post -> {
                    List<ForumPost> replies = forumPostRepository.findByParentPostOrderByCreatedAtAsc(post);
                    if (!replies.isEmpty()) {
                        forumPostLikeRepository.deleteByPostIn(replies);
                        forumPostRepository.deleteAll(replies);
                    }
                    forumPostLikeRepository.deleteByPost(post);
                    forumPostRepository.delete(post);
                    return ResponseEntity.ok(Map.of("deleted", true));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toUserResponse(AppUser user) {
        return Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "createdAt", user.getCreatedAt(),
                "blocked", blockedEmailRepository.existsByEmailIgnoreCase(user.getEmail()),
                "protectedAdmin", isProtectedAdmin(user));
    }

    private Map<String, Object> toMessageResponse(ContactMessage message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", message.getId());
        response.put("topic", message.getTopic());
        response.put("subject", message.getSubject());
        response.put("message", message.getMessage());
        response.put("createdAt", message.getCreatedAt());
        response.put("completed", message.isCompleted());
        response.put("completedAt", message.getCompletedAt());
        response.put("userName", message.getUser().getName());
        response.put("userEmail", message.getUser().getEmail());
        return response;
    }

    private Map<String, Object> toForumPostResponse(ForumPost post) {
        return Map.of(
                "id", post.getId(),
                "category", post.getCategory(),
                "title", post.getTitle(),
                "content", post.getContent(),
                "likes", forumPostLikeRepository.countByPost(post),
                "author", post.getAuthor().getName(),
                "authorEmail", post.getAuthor().getEmail(),
                "createdAt", post.getCreatedAt());
    }

    private Map<String, Object> toBlockedEmailResponse(BlockedEmail blockedEmail) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", blockedEmail.getId());
        response.put("email", blockedEmail.getEmail());
        response.put("reason", blockedEmail.getReason());
        response.put("createdAt", blockedEmail.getCreatedAt());
        return response;
    }

    private ResponseEntity<?> blockEmail(String rawEmail, String reason) {
        String email = normalizeEmail(rawEmail);
        if (email.isBlank() || !email.contains("@")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Introduce un correo electrónico válido"));
        }
        if (appUserRepository.findByEmailIgnoreCase(email).map(this::isProtectedAdmin).orElse(false)) {
            return ResponseEntity.badRequest().body(Map.of("error", "No puedes bloquear esta cuenta de administración"));
        }
        BlockedEmail blockedEmail = blockedEmailRepository.findByEmailIgnoreCase(email).orElseGet(BlockedEmail::new);
        blockedEmail.setEmail(email);
        blockedEmail.setReason(isBlank(reason) ? "Bloqueado" : reason.trim());
        return ResponseEntity.ok(toBlockedEmailResponse(blockedEmailRepository.save(blockedEmail)));
    }

    private boolean isProtectedAdmin(AppUser user) {
        return user.getRole() == UserRole.ADMIN;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}

/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.6
 * @created 05-05-2026
 * @modified 13-05-2026
 * @description API privada de foro con publicaciones, respuestas en hilo, likes y moderacion basica
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.ForumPost;
import com.yerai.racestream.model.ForumPostLike;
import com.yerai.racestream.model.UserRole;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.ForumPostLikeRepository;
import com.yerai.racestream.repository.ForumPostRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/forum")
public class ForumController {

    private static final int MAX_CATEGORY_LENGTH = 60;
    private static final int MAX_TITLE_LENGTH = 20;
    private static final int MAX_CONTENT_LENGTH = 1000;

    private final AppUserRepository appUserRepository;
    private final ForumPostRepository forumPostRepository;
    private final ForumPostLikeRepository forumPostLikeRepository;

    public ForumController(
            AppUserRepository appUserRepository,
            ForumPostRepository forumPostRepository,
            ForumPostLikeRepository forumPostLikeRepository) {
        this.appUserRepository = appUserRepository;
        this.forumPostRepository = forumPostRepository;
        this.forumPostLikeRepository = forumPostLikeRepository;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.2
     * @created 05-05-2026
     * @modified 08-05-2026
     * @description Lista publicaciones principales del foro privado con sus
     *              respuestas
     * @return Posts recientes
     */
    @GetMapping
    public List<Map<String, Object>> list(Authentication authentication) {
        AppUser user = currentUser(authentication);
        return forumPostRepository.findByParentPostIsNullOrderByCreatedAtDesc().stream()
                .map(post -> toResponse(post, user))
                .toList();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.3
     * @created 05-05-2026
     * @modified 13-05-2026
     * @description Publica un mensaje validando limites de titulo y contenido
     * @param request        Datos del post
     * @param authentication Sesión actual
     * @return Post guardado
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody PostRequest request, Authentication authentication) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Datos de publicación obligatorios"));
        }
        if (!Boolean.TRUE.equals(request.policyAccepted())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debes aceptar las normas del foro y la política de privacidad"));
        }
        String title = normalizeLimitedText(request.title(), MAX_TITLE_LENGTH);
        String content = normalizeLimitedText(request.content(), MAX_CONTENT_LENGTH);
        String category = normalizeLimitedText(isBlank(request.category()) ? "General" : request.category(), MAX_CATEGORY_LENGTH);
        String clientRequestId = normalizeLimitedText(request.clientRequestId(), 80);
        if (isBlank(title) || isBlank(content)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Título y contenido son obligatorios"));
        }
        if (isBlank(category)) {
            return ResponseEntity.badRequest().body(Map.of("error", "La categoría es obligatoria"));
        }
        if (isBlank(clientRequestId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Identificador de publicación no válido"));
        }
        if (exceeds(request.title(), MAX_TITLE_LENGTH) || exceeds(request.content(), MAX_CONTENT_LENGTH)
                || exceeds(request.clientRequestId(), 80)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Has superado el límite de caracteres"));
        }
        AppUser user = currentUser(authentication);
        var existing = forumPostRepository.findByAuthorAndClientRequestId(user, clientRequestId);
        if (existing.isPresent()) {
            return ResponseEntity.ok(toResponse(existing.get(), user));
        }
        ForumPost post = new ForumPost();
        post.setAuthor(user);
        post.setCategory(category);
        post.setTitle(title);
        post.setContent(content);
        post.setClientRequestId(clientRequestId);
        if (request.parentId() != null) {
            if (request.parentId() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Respuesta no válida"));
            }
            ForumPost parent = forumPostRepository.findById(request.parentId()).orElse(null);
            if (parent == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "La publicación original no existe"));
            }
            post.setParentPost(parent.getParentPost() == null ? parent : parent.getParentPost());
        }
        return ResponseEntity.ok(toResponse(forumPostRepository.save(post), user));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 05-05-2026
     * @modified 05-05-2026
     * @description Alterna un me gusta por usuario y publicacion
     * @param id             Identificador del post
     * @param authentication Sesión actual
     * @return Post actualizado
     */
    @PostMapping("/{id}/like")
    @Transactional
    public ResponseEntity<?> like(@PathVariable Long id, Authentication authentication) {
        AppUser user = currentUser(authentication);
        return forumPostRepository.findById(id).map(post -> {
            forumPostLikeRepository.findByPostAndUser(post, user).ifPresentOrElse(
                    forumPostLikeRepository::delete,
                    () -> {
                        ForumPostLike like = new ForumPostLike();
                        like.setPost(post);
                        like.setUser(user);
                        forumPostLikeRepository.save(like);
                    });
            post.setLikes((int) forumPostLikeRepository.countByPost(post));
            forumPostRepository.save(post);
            return ResponseEntity.ok(toResponse(post, user));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.1
     * @created 05-05-2026
     * @modified 12-05-2026
     * @description Borra un post y sus respuestas solo si lo solicita un administrador
     * @param id             Identificador del post
     * @param authentication Sesión actual
     * @return Estado de borrado
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication authentication) {
        AppUser user = currentUser(authentication);
        return forumPostRepository.findById(id).map(post -> {
            boolean allowed = user.getRole() == UserRole.ADMIN;
            if (!allowed)
                return ResponseEntity.status(403).body(Map.of("error", "Solo un administrador puede borrar mensajes del foro"));
            deletePostWithReplies(post);
            return ResponseEntity.ok(Map.of("deleted", true));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toResponse(ForumPost post, AppUser user) {
        long likes = forumPostLikeRepository.countByPost(post);
        boolean canDelete = user.getRole() == UserRole.ADMIN;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", post.getId());
        response.put("parentId", post.getParentPost() == null ? null : post.getParentPost().getId());
        response.put("category", post.getCategory());
        response.put("title", post.getTitle());
        response.put("content", post.getContent());
        response.put("likes", likes);
        response.put("likedByMe", forumPostLikeRepository.findByPostAndUser(post, user).isPresent());
        response.put("canDelete", canDelete);
        response.put("author", post.getAuthor().getName());
        response.put("createdAt", post.getCreatedAt().toString());
        response.put("replies", forumPostRepository.findByParentPostOrderByCreatedAtAsc(post).stream()
                .map(reply -> toReplyResponse(reply, user))
                .toList());
        return response;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 08-05-2026
     * @description Convierte una respuesta del foro en JSON seguro para el frontend
     * @param post Respuesta del hilo
     * @param user Usuario actual
     * @return Datos serializables de la respuesta
     */
    private Map<String, Object> toReplyResponse(ForumPost post, AppUser user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", post.getId());
        response.put("parentId", post.getParentPost() == null ? null : post.getParentPost().getId());
        response.put("category", post.getCategory());
        response.put("title", post.getTitle());
        response.put("content", post.getContent());
        response.put("likes", forumPostLikeRepository.countByPost(post));
        response.put("likedByMe", forumPostLikeRepository.findByPostAndUser(post, user).isPresent());
        response.put("canDelete", user.getRole() == UserRole.ADMIN);
        response.put("author", post.getAuthor().getName());
        response.put("createdAt", post.getCreatedAt().toString());
        return response;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 08-05-2026
     * @description Elimina una publicacion junto a respuestas y me gusta
     *              dependientes
     * @param post Publicacion a borrar
     */
    private void deletePostWithReplies(ForumPost post) {
        List<ForumPost> replies = forumPostRepository.findByParentPostOrderByCreatedAtAsc(post);
        if (!replies.isEmpty()) {
            forumPostLikeRepository.deleteByPostIn(replies);
            forumPostRepository.deleteAll(replies);
        }
        forumPostLikeRepository.deleteByPost(post);
        forumPostRepository.delete(post);
    }

    private AppUser currentUser(Authentication authentication) {
        return appUserRepository.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Recorta texto del foro al limite persistible tras limpiar
     *              espacios
     * @param value     Texto original
     * @param maxLength Longitud maxima
     * @return Texto normalizado
     */
    private String normalizeLimitedText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 11-05-2026
     * @description Detecta si un campo supera el limite definido para avisar al
     *              usuario
     * @param value     Texto original
     * @param maxLength Longitud maxima
     * @return Resultado de validacion
     */
    private boolean exceeds(String value, int maxLength) {
        return value != null && value.trim().length() > maxLength;
    }

    public record PostRequest(String category, String title, String content, Long parentId, Boolean policyAccepted, String clientRequestId) {
    }
}

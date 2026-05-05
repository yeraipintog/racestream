/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 05-05-2026
 * @modified 05-05-2026
 * @description API privada de foro con publicaciones, likes y moderacion basica
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.ForumPost;
import com.yerai.racestream.model.UserRole;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.ForumPostRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/forum")
public class ForumController {

    private final AppUserRepository appUserRepository;
    private final ForumPostRepository forumPostRepository;

    public ForumController(AppUserRepository appUserRepository, ForumPostRepository forumPostRepository) {
        this.appUserRepository = appUserRepository;
        this.forumPostRepository = forumPostRepository;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Lista publicaciones del foro privado
     * @return Posts recientes
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        return forumPostRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Publica un nuevo mensaje en el foro
     * @param request Datos del post
     * @param authentication Sesion actual
     * @return Post guardado
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody PostRequest request, Authentication authentication) {
        if (isBlank(request.title()) || isBlank(request.content())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Título y contenido son obligatorios"));
        }
        ForumPost post = new ForumPost();
        post.setAuthor(currentUser(authentication));
        post.setCategory(isBlank(request.category()) ? "General" : request.category().trim());
        post.setTitle(request.title().trim());
        post.setContent(request.content().trim());
        return ResponseEntity.ok(toResponse(forumPostRepository.save(post)));
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Suma un like a una publicacion
     * @param id Identificador del post
     * @return Post actualizado
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<?> like(@PathVariable Long id) {
        return forumPostRepository.findById(id).map(post -> {
            post.setLikes(post.getLikes() + 1);
            return ResponseEntity.ok(toResponse(forumPostRepository.save(post)));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 05-05-2026
     * @description Borra un post si lo solicita su autor o un administrador
     * @param id Identificador del post
     * @param authentication Sesion actual
     * @return Estado de borrado
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication authentication) {
        AppUser user = currentUser(authentication);
        return forumPostRepository.findById(id).map(post -> {
            boolean allowed = post.getAuthor().getId().equals(user.getId()) || user.getRole() == UserRole.ADMIN;
            if (!allowed) return ResponseEntity.status(403).body(Map.of("error", "No puedes borrar este post"));
            forumPostRepository.delete(post);
            return ResponseEntity.ok(Map.of("deleted", true));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toResponse(ForumPost post) {
        return Map.of(
                "id", post.getId(),
                "category", post.getCategory(),
                "title", post.getTitle(),
                "content", post.getContent(),
                "likes", post.getLikes(),
                "author", post.getAuthor().getName(),
                "createdAt", post.getCreatedAt().toString());
    }

    private AppUser currentUser(Authentication authentication) {
        return appUserRepository.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record PostRequest(String category, String title, String content) {}
}

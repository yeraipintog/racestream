/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 12-05-2026
 * @description Tests de permisos de borrado del foro privado
 */
package com.yerai.racestream.controller;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.ForumPost;
import com.yerai.racestream.model.UserRole;
import com.yerai.racestream.repository.AppUserRepository;
import com.yerai.racestream.repository.ForumPostLikeRepository;
import com.yerai.racestream.repository.ForumPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForumControllerTest {

    private AppUserRepository appUserRepository;
    private ForumPostRepository forumPostRepository;
    private ForumPostLikeRepository forumPostLikeRepository;
    private ForumController controller;

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Prepara repositorios mockeados para el foro
     */
    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        forumPostRepository = mock(ForumPostRepository.class);
        forumPostLikeRepository = mock(ForumPostLikeRepository.class);
        controller = new ForumController(appUserRepository, forumPostRepository, forumPostLikeRepository);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que un usuario normal no puede borrar su propio mensaje
     */
    @Test
    void normalUserCannotDeleteOwnPost() {
        AppUser user = user(1L, UserRole.USER);
        ForumPost post = post(10L, user, null);
        Authentication authentication = authentication("user@test.com");
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));
        when(forumPostRepository.findById(10L)).thenReturn(Optional.of(post));

        ResponseEntity<?> response = controller.delete(10L, authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(forumPostRepository, never()).delete(post);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que un usuario normal no puede borrar respuestas propias
     */
    @Test
    void normalUserCannotDeleteOwnReply() {
        AppUser user = user(1L, UserRole.USER);
        ForumPost parent = post(10L, user, null);
        ForumPost reply = post(11L, user, parent);
        Authentication authentication = authentication("user@test.com");
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));
        when(forumPostRepository.findById(11L)).thenReturn(Optional.of(reply));

        ResponseEntity<?> response = controller.delete(11L, authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(forumPostRepository, never()).delete(reply);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que el administrador si puede borrar mensajes y respuestas
     */
    @Test
    void adminCanDeletePostsAndReplies() {
        AppUser admin = user(2L, UserRole.ADMIN);
        AppUser user = user(1L, UserRole.USER);
        ForumPost post = post(10L, user, null);
        ForumPost reply = post(11L, user, post);
        Authentication authentication = authentication("admin@test.com");
        when(appUserRepository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(admin));
        when(forumPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(forumPostRepository.findById(11L)).thenReturn(Optional.of(reply));
        when(forumPostRepository.findByParentPostOrderByCreatedAtAsc(post)).thenReturn(List.of(reply));
        when(forumPostRepository.findByParentPostOrderByCreatedAtAsc(reply)).thenReturn(List.of());

        ResponseEntity<?> postResponse = controller.delete(10L, authentication);
        ResponseEntity<?> replyResponse = controller.delete(11L, authentication);

        assertThat(postResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(replyResponse.getStatusCode().is2xxSuccessful()).isTrue();
        verify(forumPostRepository).delete(post);
        verify(forumPostRepository).delete(reply);
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 12-05-2026
     * @description Verifica que el DTO no marca canDelete para usuarios normales
     */
    @Test
    void normalUserResponseDoesNotExposeDeletePermission() {
        AppUser user = user(1L, UserRole.USER);
        ForumPost post = post(10L, user, null);
        Authentication authentication = authentication("user@test.com");
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));
        when(forumPostRepository.findByParentPostIsNullOrderByCreatedAtDesc()).thenReturn(List.of(post));
        when(forumPostRepository.findByParentPostOrderByCreatedAtAsc(post)).thenReturn(List.of());
        when(forumPostLikeRepository.findByPostAndUser(post, user)).thenReturn(Optional.empty());

        List<java.util.Map<String, Object>> response = controller.list(authentication);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).get("canDelete")).isEqualTo(false);
    }

    @Test
    void createRejectsMissingPolicy() {
        AppUser user = user(1L, UserRole.USER);
        Authentication authentication = authentication("user@test.com");
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));

        ResponseEntity<?> response = controller.create(
                new ForumController.PostRequest("General", "Titulo", "Contenido", null, false, "req-1"),
                authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(forumPostRepository, never()).save(any());
    }

    @Test
    void duplicatedClientRequestDoesNotCreateAnotherPost() {
        AppUser user = user(1L, UserRole.USER);
        ForumPost post = post(10L, user, null);
        post.setClientRequestId("req-1");
        Authentication authentication = authentication("user@test.com");
        when(appUserRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));
        when(forumPostRepository.findByAuthorAndClientRequestId(user, "req-1")).thenReturn(Optional.of(post));
        when(forumPostRepository.findByParentPostOrderByCreatedAtAsc(post)).thenReturn(List.of());
        when(forumPostLikeRepository.findByPostAndUser(post, user)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.create(
                new ForumController.PostRequest("General", "Titulo", "Contenido", null, true, "req-1"),
                authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((java.util.Map<?, ?>) response.getBody()).get("id")).isEqualTo(10L);
        verify(forumPostRepository, never()).save(any());
    }

    private Authentication authentication(String email) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(email);
        return authentication;
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setName(role.name());
        user.setEmail(role.name().toLowerCase() + "@test.com");
        user.setRole(role);
        return user;
    }

    private ForumPost post(Long id, AppUser author, ForumPost parent) {
        ForumPost post = new ForumPost();
        post.setId(id);
        post.setAuthor(author);
        post.setParentPost(parent);
        post.setCategory("General");
        post.setTitle("Titulo");
        post.setContent("Contenido");
        post.setCreatedAt(Instant.now());
        return post;
    }
}

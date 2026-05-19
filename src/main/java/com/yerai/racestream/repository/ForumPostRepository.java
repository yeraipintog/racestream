/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.3
 * @created 05-05-2026
 * @modified 13-05-2026
 * @description Repositorio del foro privado de RaceStream con hilos de respuestas
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.ForumPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ForumPostRepository extends JpaRepository<ForumPost, Long> {
    List<ForumPost> findByParentPostIsNullOrderByCreatedAtDesc();
    List<ForumPost> findByParentPostOrderByCreatedAtAsc(ForumPost parentPost);
    List<ForumPost> findByParentPostIn(List<ForumPost> parentPosts);
    List<ForumPost> findTop30ByOrderByCreatedAtDesc();
    List<ForumPost> findByAuthor(AppUser author);
    Optional<ForumPost> findByAuthorAndClientRequestId(AppUser author, String clientRequestId);
}

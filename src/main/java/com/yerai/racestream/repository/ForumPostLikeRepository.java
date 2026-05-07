/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.1
 * @created 05-05-2026
 * @modified 06-05-2026
 * @description Repositorio de me gusta del foro para alternar el estado por usuario
 */
package com.yerai.racestream.repository;

import com.yerai.racestream.model.AppUser;
import com.yerai.racestream.model.ForumPost;
import com.yerai.racestream.model.ForumPostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ForumPostLikeRepository extends JpaRepository<ForumPostLike, Long> {
    long countByPost(ForumPost post);
    void deleteByPost(ForumPost post);
    void deleteByPostIn(List<ForumPost> posts);
    void deleteByUser(AppUser user);
    Optional<ForumPostLike> findByPostAndUser(ForumPost post, AppUser user);
}
